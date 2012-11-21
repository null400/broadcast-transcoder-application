package dk.statsbiblioteket.broadcasttranscoder.fetcher.cli;

import dk.statsbiblioteket.broadcasttranscoder.cli.AbstractOptionsParser;
import dk.statsbiblioteket.broadcasttranscoder.cli.Context;
import dk.statsbiblioteket.broadcasttranscoder.cli.OptionParseException;
import dk.statsbiblioteket.broadcasttranscoder.fetcher.BtaDomsFetcher;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 */
public class OptionsParser extends AbstractOptionsParser{

    protected static final Option PID_OPTION = new Option("programpid", true, "The DOMS pid of the program to be transcoded");
    protected static final Option INFRASTRUCTURE_CONFIG_FILE_OPTION = new Option("infrastructure_configfile", true, "The infrastructure config file");
    protected static final Option BEHAVIOURAL_CONFIG_FILE_OPTION = new Option("behavioural_configfile", true, "The behavioural config file");
    protected static final Option USAGE_OPTION = new Option("u", false, "Usage");

    protected static Options options;

    private FetcherContext context;

    public OptionsParser() {
        context = new FetcherContext();
        options = new Options();
        options.addOption(PID_OPTION);
        options.addOption(INFRASTRUCTURE_CONFIG_FILE_OPTION);
        options.addOption(BEHAVIOURAL_CONFIG_FILE_OPTION);


    }


    public FetcherContext parseOptions(String[] args) throws OptionParseException {
        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            parseError(e.toString());
            throw new OptionParseException(e.getMessage(), e);
        }
        parseUsageOption(cmd);
        parseInfrastructureConfigFileOption(cmd);
        parseBehaviouralConfigFileOption(cmd);
        try {
            readInfrastructureProperties(context);
            readFetcherProperties(context);
        } catch (IOException e) {
            throw new OptionParseException("Error reading properties.", e);
        }
        return context;
    }

    protected void parseUsageOption(CommandLine cmd) {
        if (cmd.hasOption(USAGE_OPTION.getOpt())) {
            printUsage();
            System.exit(0);
        }
    }

    protected void readFetcherProperties(FetcherContext context) throws IOException, OptionParseException {
        Properties props = new Properties();
        props.load(new FileInputStream(context.getBehaviourConfigFile()));
        context.setViewAngle(readStringProperty(PropertyNames.VIEW_ANGLE,props));
        context.setCollection(readStringProperty(PropertyNames.COLLECTION, props));
        context.setState(readStringProperty(PropertyNames.STATE, props));
        context.setBatchSize(readIntegerProperty(PropertyNames.BATCH_SIZE, props));
    }



    protected void readInfrastructureProperties(Context context) throws IOException, OptionParseException {
        Properties props = new Properties();
        props.load(new FileInputStream(context.getInfrastructuralConfigFile()));

        context.setFileOutputRootdir(readExistingDirectoryProperty(PropertyNames.FILE_DIR, props));
        context.setPreviewOutputRootdir(readExistingDirectoryProperty(PropertyNames.PREVIEW_DIR, props));
        context.setSnapshotOutputRootdir(readExistingDirectoryProperty(PropertyNames.SNAPSHOT_DIR, props));
        context.setLockDir(readExistingDirectoryProperty(PropertyNames.LOCK_DIR, props));
        context.setFileDepth(readIntegerProperty(PropertyNames.FILE_DEPTH, props));
        context.setFileFinderUrl(readStringProperty(PropertyNames.FILE_FINDER, props));
        context.setMaxFilesFetched(readIntegerProperty(PropertyNames.MAX_FILES_FETCHED, props));

        context.setDomsEndpoint(readStringProperty(PropertyNames.DOMS_ENDPOINT, props));
        context.setDomsUsername(readStringProperty(PropertyNames.DOMS_USER, props));
        context.setDomsPassword(readStringProperty(PropertyNames.DOMS_PASSWORD, props));
    }



    protected void parseInfrastructureConfigFileOption(CommandLine cmd) throws OptionParseException {
        String configFileString = cmd.getOptionValue(INFRASTRUCTURE_CONFIG_FILE_OPTION.getOpt());
        if (configFileString == null) {
            parseError(INFRASTRUCTURE_CONFIG_FILE_OPTION.toString());
            throw new OptionParseException(INFRASTRUCTURE_CONFIG_FILE_OPTION.toString());
        }
        File configFile = new File(configFileString);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new OptionParseException(configFile.getAbsolutePath() + " is not a file.");
        }
        context.setInfrastructuralConfigFile(configFile);
    }

    protected void parseBehaviouralConfigFileOption(CommandLine cmd) throws OptionParseException {
        String configFileString = cmd.getOptionValue(BEHAVIOURAL_CONFIG_FILE_OPTION.getOpt());
        if (configFileString == null) {
            parseError(BEHAVIOURAL_CONFIG_FILE_OPTION.toString());
            throw new OptionParseException(BEHAVIOURAL_CONFIG_FILE_OPTION.toString());
        }
        File configFile = new File(configFileString);
        if (!configFile.exists() || configFile.isDirectory()) {
            throw new OptionParseException(configFile.getAbsolutePath() + " is not a file.");
        }
        context.setBehaviourConfigFile(configFile);
    }



    protected void printUsage() {
        final HelpFormatter usageFormatter = new HelpFormatter();
        usageFormatter.printHelp(BtaDomsFetcher.class.getName(), options, true);
    }

    protected void parseError(String message) {
        System.err.println("Error parsing arguments");
        System.err.println(message);
        printUsage();
    }

}
