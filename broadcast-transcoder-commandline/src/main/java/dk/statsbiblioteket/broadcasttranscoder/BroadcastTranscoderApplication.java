package dk.statsbiblioteket.broadcasttranscoder;

import dk.statsbiblioteket.broadcasttranscoder.cli.OptionParseException;
import dk.statsbiblioteket.broadcasttranscoder.cli.SingleTranscodingContext;
import dk.statsbiblioteket.broadcasttranscoder.cli.UsageException;
import dk.statsbiblioteket.broadcasttranscoder.cli.parsers.SingleTranscodingOptionsParser;
import dk.statsbiblioteket.broadcasttranscoder.persistence.entities.TranscodingRecord;
import dk.statsbiblioteket.broadcasttranscoder.processors.*;
import dk.statsbiblioteket.broadcasttranscoder.persistence.entities.BroadcastTranscodingRecord;
import dk.statsbiblioteket.broadcasttranscoder.persistence.dao.BroadcastTranscodingRecordDAO;
import dk.statsbiblioteket.broadcasttranscoder.persistence.dao.HibernateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class BroadcastTranscoderApplication extends TranscoderApplication{

    private static Logger logger = LoggerFactory.getLogger(BroadcastTranscoderApplication.class);

    public static void main(String[] args) throws Exception {
        logger.debug("Entered main method.");
        SingleTranscodingContext<BroadcastTranscodingRecord> context = null;
        TranscodeRequest request = null;
        try {
            try {
                context = new SingleTranscodingOptionsParser<BroadcastTranscodingRecord>().parseOptions(args);
            } catch (UsageException e) {
                return;
            }
            HibernateUtil util = HibernateUtil.getInstance(context.getHibernateConfigFile().getAbsolutePath());
            context.setTranscodingProcessInterface(new BroadcastTranscodingRecordDAO(util));
            request = new TranscodeRequest();
            request.setObjectPid(context.getProgrampid());
        } catch (Exception e) {
            logger.error("Error in initial environment", e);
            throw new OptionParseException("Failed to parse optioons",e);
        }
        try {
            runChain(request, context);
            if (request.isRejected()) {
                logger.warn("Request for pid "+request.getObjectPid()+" was rejected");
                System.exit(0);
            }
        } catch (Exception e) {
            //Final fault barrier is necessary for logging
            logger.error("Processing failed for " + request.getObjectPid(), e);
            transcodingFailed(request,context,e);
            throw e;
        } finally {
            logger.info("All processing finished for " + request.getObjectPid());
        }
    }



    public static <T extends TranscodingRecord> void runChain(TranscodeRequest request, SingleTranscodingContext<T> context) throws ProcessorException {

        request.setGoForTranscoding(true);

        ProcessorChainElement structureFetcher = new DomsAndOverwriteExaminerProcessor();
        ProcessorChainElement preChain = ProcessorChainElement.makeChain(
                structureFetcher);
        preChain.processIteratively(request, context);

        if (!request.isGoForTranscoding()) {
            alreadyTranscoded(request,context);
            return;
        }


            /*Next one getting for the persistence layer*/
        ProcessorChainElement pbcorer = new PbcoreMetadataExtractorProcessor();

        ProcessorChainElement metadataChain = ProcessorChainElement.makeChain(pbcorer);
        metadataChain.processIteratively(request,context);

            /*First one getting stuff for the persistence layer*/
        ProcessorChainElement programFetcher = new ProgramMetadataFetcherProcessor();
        ProcessorChainElement filedataFetcher    = new FileMetadataFetcherProcessor();
        ProcessorChainElement sanitiser = new SanitiseBroadcastMetadataProcessor();
        ProcessorChainElement sorter = new BroadcastMetadataSorterProcessor();
        ProcessorChainElement fileFinderFetcher = new OnlineFilefinderProcessor();
        ProcessorChainElement identifier = new FilePropertiesIdentifierProcessor();

            /*Find the offsets*/
        ProcessorChainElement clipper = new ClipFinderProcessor();

        ProcessorChainElement coverage = new CoverageAnalyserProcessor();
        ProcessorChainElement updater = new ProgramStructureUpdaterProcessor();
        ProcessorChainElement fixer = new StructureFixerProcessor();
        ProcessorChainElement concatenator = new ClipConcatenatorProcessor();
        ProcessorChainElement firstChain = ProcessorChainElement.makeChain(
                programFetcher,
                filedataFetcher,
                sanitiser,
                sorter,
                fileFinderFetcher,
                identifier,
                clipper,
                coverage,
                updater,
                fixer,
                concatenator);
        firstChain.processIteratively(request, context);
        if (!request.isGoForTranscoding()) {
            alreadyTranscoded(request, context);
            return;
        }
        if (request.isRejected()) {
            reject(request, context);
            return;
        }
        ProcessorChainElement secondChain;
        ProcessorChainElement pider = new PidAndAsepctRatioExtractorProcessor();
        ProcessorChainElement waver = new WavTranscoderProcessor();
        ProcessorChainElement multistreamer = new MultistreamVideoTranscoderProcessor();
        ProcessorChainElement unistreamvideoer = new UnistreamVideoTranscoderProcessor();
        ProcessorChainElement unistreamaudioer = new UnistreamAudioTranscoderProcessor();
        ProcessorChainElement renamer = new FinalMediaFileRenamerProcessor();
        ProcessorChainElement previewer = new PreviewClipperProcessor();
        ProcessorChainElement snapshotter = new SnapshotExtractorProcessor();
        ProcessorChainElement zeroChecker = new ZeroLengthCheckerProcessor();
        ProcessorChainElement persistenceEnricher = new BroadcastTranscodingRecordEnricherProcessor();

        switch (request.getFileFormat()) {
            case MULTI_PROGRAM_MUX:
                if (context.getVideoOutputSuffix().equals("mpeg")) {
                    logger.debug("Generating DVD video. No previews or snapshots for " + request.getObjectPid());
                    secondChain = ProcessorChainElement.makeChain(pider,
                            multistreamer,
                            renamer,
                            zeroChecker
                    );
                } else {
                    secondChain = ProcessorChainElement.makeChain(pider,
                        multistreamer,
                        renamer,
                        zeroChecker,
                        previewer,
                        snapshotter);
                }
                break;
            case SINGLE_PROGRAM_VIDEO_TS:
                if (context.getVideoOutputSuffix().equals("mpeg")) {
                    logger.debug("Generating DVD video. No previews or snapshots for " + request.getObjectPid());
                    secondChain = ProcessorChainElement.makeChain(pider,
                            unistreamvideoer,
                            renamer,
                            zeroChecker
                    );
                } else {
                    secondChain = ProcessorChainElement.makeChain(pider,
                         unistreamvideoer,
                         renamer,
                         zeroChecker,
                         previewer,
                         snapshotter);
                }
                break;
            case SINGLE_PROGRAM_AUDIO_TS:
                secondChain = ProcessorChainElement.makeChain(pider,
                        unistreamaudioer,
                        renamer,
                        zeroChecker,
                        previewer);
                break;
            case MPEG_PS:
                if (context.getVideoOutputSuffix().equals("mpeg")) {
                    logger.debug("Generating DVD video. No previews or snapshots for " + request.getObjectPid());
                    secondChain = ProcessorChainElement.makeChain(pider,
                            unistreamvideoer,
                            renamer,
                            zeroChecker
                    );
                } else {
                    secondChain = ProcessorChainElement.makeChain(pider,
                            unistreamvideoer,
                            renamer,
                            zeroChecker,
                            previewer,
                            snapshotter);
                }
                break;
            case AUDIO_WAV:
                secondChain = ProcessorChainElement.makeChain(waver,
                        renamer,
                        zeroChecker,
                        previewer);
                break;
            default:
                return;
        }
        secondChain.processIteratively(request, context);

        transcodingComplete(request,context);

        ProcessorChainElement thirdChain = ProcessorChainElement.makeChain(persistenceEnricher);
        try {
            thirdChain.processIteratively(request, context);
        } catch (ProcessorException e) {
            //This is only a warning. Enrichment is only a nice-to-have.
            logger.warn("Persistence Enrichment failed for " + request.getObjectPid(), e);
        }
    }


}
