/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.scripts.handler.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.Process;
import org.dspace.scripts.factory.ScriptServiceFactory;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.dspace.scripts.service.ProcessService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * This is an implementation for the CommandLineDSpaceRunnables which means that these implementations
 * are used by DSpaceRunnables which are called from the CommandLine
 */
public class CommandLineDSpaceRunnableHandler implements DSpaceRunnableHandler {
    private static final Logger log = LogManager
        .getLogger(CommandLineDSpaceRunnableHandler.class);
    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();
    private final boolean IS_SAVE_ENABLED = isSaveEnabled();
    private final ProcessService processService = ScriptServiceFactory.getInstance().getProcessService();
    private final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private Integer processId;
    private String scriptName;
    private List<DSpaceCommandLineParameter> parameters;

    public CommandLineDSpaceRunnableHandler() {
    }

    public CommandLineDSpaceRunnableHandler(String scriptName, List<DSpaceCommandLineParameter> parameters) {
        this.scriptName = scriptName;
        this.parameters = parameters;
    }

    @Override
    public void start() {
        System.out.println("The script has started");
        if (IS_SAVE_ENABLED) {
            UUID ePersonUUID = null;
            Context context = new Context();
            try {
                EPerson ePerson = null;
                String parameter = parameters.get(0).getName();
                if (parameter.contains("-e")) {
                    String email = Arrays.stream(parameter.split(" "))
                        .filter(s -> s.contains("@")).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No email found in parameters"));
                    ePerson = ePersonService.findByEmail(context, email);
                    if (ePerson == null) {
                        throw new IllegalArgumentException("No eperson found with email: " + email);
                    }
                    ePersonUUID = ePerson.getID();
                }
                Process process = processService.create(context, ePerson, scriptName, parameters,
                    new HashSet<>(context.getSpecialGroups()));
                processId = process.getID();
                processService.start(context, process);
                context.complete();
            } catch (Exception e) {
                if (ePersonUUID != null) {
                    logError(
                        "CommandLineDspaceRunnableHandler with ePerson: " + ePersonUUID +
                            " for Script with name: " +
                            scriptName +
                            " and parameters: " + parameters + " could not be created", e);
                } else {
                    logError(
                        "CommandLineDspaceRunnableHandler with command-line user for Script with name: " +
                            scriptName +
                            " and parameters: " + parameters + " could not be created", e);
                }
            } finally {
                context.close();
            }
        }
    }


    @Override
    public void handleCompletion() {
        System.out.println("The script has completed");
        if (IS_SAVE_ENABLED) {
            Context context = new Context();
            try {
                Process process = processService.find(context, processId);
                processService.complete(context, process);
                context.complete();
            } catch (SQLException e) {
                logError("CommandLineDSpaceRunnableHandler with process: " + processId + " could not be completed", e);
            } catch (Exception e) {
                logError(e.getMessage(), e);
            } finally {
                context.close();
            }
        }
    }

    @Override
    public void handleException(Exception e) {
        handleException(null, e);
    }

    @Override
    public void handleException(String message) {
        handleException(message, null);
    }

    @Override
    public void handleException(String message, Exception e) {
        if (message != null) {
            System.err.println(message);
            log.error(message);
        }
        if (e != null) {
            e.printStackTrace();
            log.error(e.getMessage(), e);
        }

        if (IS_SAVE_ENABLED) {
            Context context = new Context();
            try {
                Process process = processService.find(context, processId);
                processService.fail(context, process);
                context.complete();
            } catch (SQLException sqlException) {
                logError("SQL exception while handling another exception", e);
            } catch (Exception exception) {
                logError(exception.getMessage(), exception);
            } finally {
                context.close();
            }
        }

        System.exit(1);
    }

    @Override
    public void logDebug(String message) {
        log.debug(message);
    }

    @Override
    public void logInfo(String message) {
        System.out.println(message);
        log.info(message);
    }

    @Override
    public void logWarning(String message) {
        System.out.println(message);
        log.warn(message);
    }

    @Override
    public void logError(String message) {
        System.err.println(message);
        log.error(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        System.err.println(message);
        log.error(message, throwable);
    }

    @Override
    public void printHelp(Options options, String name) {
        if (options != null) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(name, options);
        }
    }

    @Override
    public Optional<InputStream> getFileStream(Context context, String fileName) throws IOException {
        File file = new File(fileName);
        if (!(file.exists() && file.isFile())) {
            return Optional.empty();
        }
        return Optional.of(FileUtils.openInputStream(file));
    }

    @Override
    public void writeFilestream(Context context, String fileName, InputStream inputStream, String type)
        throws IOException {
        File file = new File(fileName);
        FileUtils.copyInputStreamToFile(inputStream, file);
    }

    @Override
    public List<UUID> getSpecialGroups() {
        return Collections.emptyList();
    }

    /**
     * Check if the save option is enabled in the configuration
     *
     * @return true if the save option is enabled, false otherwise
     */
    private boolean isSaveEnabled() {
        return configurationService.getBooleanProperty("process.save-enable", false);
    }

}
