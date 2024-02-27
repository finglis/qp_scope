package qupath.ext.qp_scope.utilities

import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.ext.qp_scope.ui.UI_functions
import qupath.lib.gui.QuPathGUI

import qupath.lib.gui.scripting.QPEx
import qupath.lib.images.ImageData
import qupath.lib.objects.PathObject
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.RectangleROI
import qupath.lib.roi.interfaces.ROI
import qupath.lib.scripting.QP

import qupath.lib.projects.Project

import qupath.lib.objects.PathObjects
import qupath.ext.basicstitching.stitching.StitchingImplementations
import java.util.concurrent.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import qupath.ext.qp_scope.utilities.PythonTestScripts

class UtilityFunctions {

    static final logger = LoggerFactory.getLogger(UtilityFunctions.class)




    /**
     * Performs image stitching, renames the stitched image, and updates the QuPath project with the renamed image.
     * The new name of the stitched image is based on the sample label, annotation name, and the original image name.
     * If the annotation name is "bounds", it is omitted from the new file name.
     *
     * @param projectsFolderPath The path where the project is located and where the stitched image will be saved.
     * @param sampleLabel The label for the sample, used as part of the new file name.
     * @param scanTypeWithIndex The scan type with an appended index for uniqueness, used in stitching process.
     * @param annotationName The name of the annotation, used as part of the new file name unless it is "bounds".
     * @param qupathGUI The QuPath GUI instance used for updating the project.
     * @param currentQuPathProject The current QuPath project to which the stitched image will be added.
     * @param compression The type of compression to use for stitching (default is "J2K_LOSSY").
     * @return The path to the renamed stitched image.
     */
    static String stitchImagesAndUpdateProject(String projectsFolderPath, String sampleLabel,
                                               String scanTypeWithIndex, String annotationName, QuPathGUI qupathGUI,
                                               Project currentQuPathProject,
                                               String compression = "J2K_LOSSY") {

        String stitchedImageOutputFolder = projectsFolderPath + File.separator + sampleLabel + File.separator + "SlideImages"
        String tileImageInputFolder = projectsFolderPath + File.separator + sampleLabel + File.separator + scanTypeWithIndex

        logger.info("Calling stitchCore with $tileImageInputFolder")
        String stitchedImagePathStr = StitchingImplementations.stitchCore("Coordinates in TileConfiguration.txt file",
                tileImageInputFolder,
                stitchedImageOutputFolder,
                compression,
                0,
                1,
                annotationName)

        File stitchedImagePath = new File(stitchedImagePathStr)
        String adjustedFileName = sampleLabel+ '_' + scanTypeWithIndex + '_'+ (annotationName.equals("bounds") ? "" : annotationName)
        File adjustedFilePath = new File(stitchedImagePath.parent, adjustedFileName)

        // Rename the stitched image file
        if (stitchedImagePath.renameTo(adjustedFilePath)) {
            stitchedImagePathStr = adjustedFilePath.absolutePath
        }
       Platform.runLater {
            logger.info("Platform.runLater section of stitchImagesAndUpdateProject")
            // Add the (possibly renamed) image to the project
            QPProjectFunctions.addImageToProject(adjustedFilePath, currentQuPathProject)
            def matchingImage = currentQuPathProject.getImageList().find { image ->
                new File(image.getImageName()).name == adjustedFilePath.name
            }

            qupathGUI.openImageEntry(matchingImage)
            qupathGUI.setProject(currentQuPathProject)
            qupathGUI.refreshProject()
        }
        return stitchedImagePathStr
    }

//    /**
//     * Executes a Python script using a specified Python executable within a virtual environment.
//     * This method is designed to be compatible with Windows, Linux, and macOS.
//     *
//     * @param anacondaEnvPath The path to the Python virtual environment.
//     * @param pythonScriptPath The path to the Python script in Preferences to run the microscope.
//     * @param arguments A list of arguments to pass to the python script. The amount may vary, and different scripts will be run depending on the number of arguments passed
//     */
//    static runPythonCommand(String anacondaEnvPath, String pythonScriptPath, List arguments) {
//        try {
//
//            String pythonExecutable = new File(anacondaEnvPath, "python.exe").getCanonicalPath()
//
//            File scriptFile = new File(pythonScriptPath)
//
//            // Adjust the pythonScriptPath based on arguments
//            if (arguments == null) {
//                // Change the script to 'getStageCoordinates.py'
//
//                def getStageScriptPath = new File(scriptFile.getParent(), "getStageCoordinates.py").getCanonicalPath()
//                logger.info("calling runPythonCommand on $getStageScriptPath")
//                if (!(new File(getStageScriptPath).exists())) {
//                    // If the file does not exist, call runTestPythonScript
//                    return runTestPythonScript(anacondaEnvPath, getStageScriptPath, arguments)
//                }
//                // Construct the command
//                String command = "\"" + pythonExecutable + "\" -u \"" + getStageScriptPath + "\" " + arguments
//                // Execute the command
//                Process process = command.execute()
//                logger.info("Executing command: " + command)
//                logger.info("This should get stage coordinates back")
//                List<String> result = handleProcessOutput(process)
//                if (result != null) {
//                    logger.info("Received output: ${result.join(', ')}")
//                    return result
//                } else {
//                    logger.error("Error occurred or no valid output received from the script.")
//                    return null
//                }
//            }
//            //If only two arguments are passed, we assume that a command needs to be sent to move the stage.
//            if (arguments.size() == 2) {
//                pythonScriptPath = new File(scriptFile.parent, "moveStageToCoordinates.py").canonicalPath
//            }
//
//            logger.info("calling runPythonCommand on $pythonScriptPath")
//            if (!(new File(pythonScriptPath).exists())) {
//                // If the file does not exist, call runTestPythonScript
//                return runTestPythonScript(anacondaEnvPath, pythonScriptPath, arguments)
//            }
//
//            //Run the correct Python script! Either moe the stage to a point, or perform a collection
//            //
//            String args = arguments != null ? arguments.collect { "\"$it\"" }.join(' ') : ""
//
//            // Construct the command
//            String command = "\"" + pythonExecutable + "\" -u \"" + pythonScriptPath + "\" " + args
//            logger.info("Executing command: " + command)
//
//            // Execute the command
//            Process process = command.execute()
//            // Read the full output from the process
//            process.waitFor()
//            String output = process.in.text
//            String errorOutput = process.err.text
//
//            if (output) {
//                logger.info("Received output: \n$output")
//            }
//            if (errorOutput) {
//                logger.error("Error output: \n$errorOutput")
//            }
//
//            return null
//        } catch (Exception e) {
//            e.printStackTrace()
//        }
//    }
//
//    static List<String> handleProcessOutput(Process process) {
//        BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))
//        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))
//
//        String line
//        List<String> outputLines = []
//        List<String> errorLines = []
//        String value1 = null
//        String value2 = null
//
//        while ((line = outputReader.readLine()) != null) {
//            outputLines.add(line)
//            // Assuming coordinates are on the first line
//            if (outputLines.size() == 1) {
//                String[] values = line.split(" ")
//                value1 = values[0]
//                value2 = values[1]
//            }
//        }
//
//        while ((line = errorReader.readLine()) != null) {
//            errorLines.add(line)
//        }
//        // Capture and log the process exit code
//        int exitCode = process.waitFor();
//        logger.info("Process exit code: " + exitCode);
//
//        // Log any error output from the process
//        if (!errorLines.isEmpty()) {
//            logger.error("Error output from Python script: \n" + String.join("\n", errorLines));
//        }
//        // Check for errors or invalid output
//        if (!errorLines.isEmpty() || value1 == null || value2 == null) {
//            return null
//        }
//
//        return [value1, value2]
//    }


    //TODO fix runTestPythonScript calls

//                if (!(new File(getStageScriptPath).exists())) {
//                    // If the file does not exist, call runTestPythonScript
//                    return runTestPythonScript(anacondaEnvPath, getStageScriptPath, arguments)
//
//                }

//            if (!(new File(pythonScriptPath).exists())) {
//                // If the file does not exist, call runTestPythonScript
//                return runTestPythonScript(anacondaEnvPath, pythonScriptPath, arguments)
//
//            }
/**
 * Executes a Python script using a specified Python executable within a virtual environment and handles live output.
 * This method can handle different scripts and arguments, including cases where no arguments are provided and two values are expected from the output.
 *
 * @param anacondaEnvPath The path to the Python virtual environment.
 * @param pythonScriptPath The path to the Python script.
 * @param arguments A list of arguments to pass to the Python script; can be null.
 * @return A List<String> with the output values if specific output handling is required; otherwise, null.
 */

    static List<String> runPythonCommand(String anacondaEnvPath, String pythonScriptPath, List<String> arguments) {
        AtomicInteger tifCount = new AtomicInteger(0);

        AtomicReference<String> value1 = new AtomicReference<>();
        AtomicReference<String> value2 = new AtomicReference<>();
        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        List<String> tifLines = new ArrayList<>(); // Store .tif lines here
        String argsJoined = arguments != null ? arguments.stream().map(arg -> "\"" + arg + "\"").collect(Collectors.joining(" ")) : "";
        int totalTifFiles = 0
        boolean progressBar = false

        try {
            String pythonExecutable = new File(anacondaEnvPath, "python.exe").getCanonicalPath();
            File scriptFile = new File(pythonScriptPath);

            // Adjust script path and logger message for null arguments or specific size of arguments
            if (arguments == null) {
                pythonScriptPath = new File(new File(pythonScriptPath).getParent(), "getStageCoordinates.py").getCanonicalPath();
                logger.info("Running getStageCoordinates script");
            } else if (arguments.size() == 2) {
                pythonScriptPath = new File(scriptFile.getParent(), "moveStageToCoordinates.py").getCanonicalPath();
            } else {
                totalTifFiles = MinorFunctions.countTifEntriesInTileConfig(arguments);
                progressBar = true
                //logger.info("SHOWING PROGRESS BAR NOW FOR $totalTifFiles TIFF FILES after")
            }


            String command = String.format("\"%s\" -u \"%s\" %s", pythonExecutable, pythonScriptPath, argsJoined);
            logger.info("Running Python Command as follows")
            logger.info("$command")
            Process process = Runtime.getRuntime().exec(command);
            if (progressBar) {
                UI_functions.showProgressBar(tifCount, totalTifFiles, process, 10000)
                //UI_functions.showProgressBar(tifCount, totalTifFiles)
            }

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            //This section handles the output from the python script, and what impact it has on the rest of the workflow
            Thread outputThread = new Thread(() -> {
                outputReader.lines().forEach(line -> {
                    logger.info("Output: " + line);
                    if (line.contains(".tif")) {

                        tifLines.add(line); // Add .tif line to the list
                        tifCount.incrementAndGet();
                        logger.info("Line and $tifCount count")
                    } else if (arguments == null || arguments.size() == 2) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            value1.set(parts[0]);
                            value2.set(parts[1]);
                        }
                    }
                });
            });

            //Handle error output related to the python process, destroying it if necessary.
            Thread errorThread = new Thread(() -> {
                String errorLine;
                try {
                    while ((errorLine = errorReader.readLine()) != null) {
                        logger.error("Error: " + errorLine);
                        if (errorLine.equals("Exiting")) {
                            process.destroy();
                            return;
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading script error output", e);
                } finally {
                    try {
                        errorReader.close();
                    } catch (IOException e) {
                        logger.error("Error closing the error stream", e);
                    }
                }
            });

            outputThread.start();
            errorThread.start();

            outputThread.join(); // Ensure output processing completes
            errorThread.join(); // Ensure error processing completes

            int exitCode = process.waitFor();
            logger.info("Python process exited with code: " + exitCode);
            if (errorOccurred.get()) {
                return null; // Or handle error differently
            }

            if (arguments == null || arguments.size() == 2) {
                return Arrays.asList(value1.get(), value2.get());
            } else {
                return tifLines; // Return collected .tif lines
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Default return in case of unexpected failure
    }


/**
 * Executes a test Python script based on the provided script path. This method selects
 * an appropriate test script and runs it using the Python interpreter at the specified path.
 *
 * @param anacondaEnvPath The path to the Python virtual environment.
 * @param pythonScriptPath The path to the Python script, used to determine which test script to run.
 * @param arguments A list of arguments to pass to the python script.
 * @return The output from the Python script execution, or null in case of an error.
 */

    static runTestPythonScript(String anacondaEnvPath, String pythonScriptPath, List arguments) {
        Logger logger2 = LoggerFactory.getLogger(QuPathGUI.class);
        logger2.warn("Default script not found, running test script for $pythonScriptPath")
        String pythonScript = "";
        logger.info("calling runtestpythoncommand on $pythonScriptPath")
        // Determine which test script to use based on the file name
        String scriptName = new File(pythonScriptPath).getName();

        if (scriptName.equalsIgnoreCase("getStageCoordinates.py")) {
            pythonScript = PythonTestScripts.pyTestGetStageCoordinates();
        } else if (scriptName.equalsIgnoreCase("moveStageToCoordinates.py")) {
            pythonScript = PythonTestScripts.pyTestSendStageCoordinates();
        } else if (scriptName.equalsIgnoreCase("4x_bf_scan_pycromanager.py")) {
            pythonScript = PythonTestScripts.pyFauxMicroscopeAcquisition();
        }

        // Execute the selected Python script
        try {
            // Command to start Python interpreter
            logger.info("Running test replacement python command for $pythonScriptPath ")
            String pythonExecutable = new File(anacondaEnvPath, "python.exe").getCanonicalPath();
            ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, "-u", "-");
            Process process = processBuilder.start();

            // Write the Python script to the process's standard input
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            if (arguments != null && !arguments.isEmpty()) {
                String argsString = arguments.stream()
                        .map(arg -> "\"" + arg.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                        .collect(Collectors.joining(", "));
                pythonScript += "\nimport sys\nsys.argv.extend([" + argsString + "])";
            }


            writer.write(pythonScript);
            writer.flush();
            writer.close();
            if (scriptName.equalsIgnoreCase("getStageCoordinates.py")) {
                logger.info("entering getStageCoordinates code block")
                logger.info(pythonScript)
                // Use handleProcessOutput for getStageCoordinates.py
                List<String> result = handleProcessOutput(process);
                if (result != null) {
                    logger.info("Received output: ${result.join(', ')}");
                    return result;
                } else {
                    logger.error("Error occurred or no valid output received from the script.");
                    return null;
                }
            } else {
                // Read process output
                BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                StringBuilder output = new StringBuilder();
                String line;
                while ((line = outputReader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }

                int exitCode = process.waitFor();
                logger.info("Exit code: " + exitCode);

                if (!errorOutput.toString().isEmpty()) {
                    logger.error("Error output: \n" + errorOutput.toString());
                }

                if (exitCode != 0) {
                    logger.error("Error output: \n" + errorOutput.toString());
                    return null;
                }
                return output.toString();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Deletes all the tiles within the provided folder and the folder itself.
     *
     * @param folderPath The path to the folder containing the tiles to be deleted.
     */

    static void deleteTilesAndFolder(String folderPath) {

        try {

            Path directory = Paths.get(folderPath)

            // Delete all files in the folder
            Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path)
                        } catch (IOException ex) {
                            logger.error("Error deleting file: " + path, ex)
                        }
                    })

            // Delete the folder itself
            Files.delete(directory)
        } catch (IOException ex) {
            logger.error("Error deleting folder: " + folderPath, ex)
        }
    }

    static void zipTilesAndMove(String folderPath) {

        try {
            Path directory = Paths.get(folderPath)
            Path parentDirectory = directory.getParent()
            Path compressedTilesDir = parentDirectory.resolve("Compressed tiles")

            // Create "Compressed tiles" directory if it doesn't exist
            if (!Files.exists(compressedTilesDir)) {
                Files.createDirectory(compressedTilesDir)
            }

            // Create a Zip file
            String zipFileName = directory.getFileName().toString() + ".zip"
            Path zipFilePath = compressedTilesDir.resolve(zipFileName)
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                Files.walk(directory)
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            ZipEntry zipEntry = new ZipEntry(directory.relativize(path).toString())
                            try {
                                zos.putNextEntry(zipEntry)
                                Files.copy(path, zos)
                                zos.closeEntry()
                            } catch (IOException ex) {
                                logger.error("Error adding file to zip: " + path, ex)
                            }
                        })
            }

            // Optionally, delete the original tiles and folder
            // deleteTilesAndFolder(folderPath);
        } catch (IOException ex) {
            logger.error("Error zipping and moving tiles from: " + folderPath, ex)
        }
    }


    /**
     * Modifies the specified Groovy script by updating the pixel size and the JSON file path.
     *
     * @param groovyScriptPath The path to the Groovy script file.
     * @param pixelSize The new pixel size to set in the script.
     * @param jsonFilePathString The new JSON file path to set in the script.
     * @throws IOException if an I/O error occurs reading from or writing to the file.
     */
    static String modifyTissueDetectScript(String groovyScriptPath, String pixelSize, String jsonFilePathString) throws IOException {
        // Read, modify, and write the script in one go
        logger.info("GroovyScriptPath $groovyScriptPath")
        logger.info("pixelSize $pixelSize")
        logger.info("jsonFilePathString $jsonFilePathString")
        List<String> lines = Files.lines(Paths.get(groovyScriptPath), StandardCharsets.UTF_8)
                .map(line -> {
                    if (line.startsWith("setPixelSizeMicrons")) {
                        return "setPixelSizeMicrons(" + pixelSize + ", " + pixelSize + ")"
                    } else if (line.startsWith("createAnnotationsFromPixelClassifier")) {
                        return line.replaceFirst("\"[^\"]*\"", "\"" + jsonFilePathString + "\"")
                    } else {
                        return line
                    }
                })
                .collect(Collectors.toList())

        return String.join(System.lineSeparator(), lines)
    }



/**
 * Performs tiling and saves configuration based on either bounding box coordinates or existing annotations.
 *
 * @param baseDirectory The base directory where the tiles will be saved.
 * @param imagingModality The type of imaging modality used, e.g., '4x-bf'.
 * @param frameWidth The width of each tile in either pixels (annotations) or microns (bounding box).
 * @param frameHeight The height of each tile in in either pixels (annotations) or microns (bounding box).
 * @param overlapPercent The percent overlap between adjacent tiles.
 * @param boundingBoxCoordinates The coordinates for the bounding box on the microscope stage in microns if provided, otherwise null.
 * @param createTiles Flag to determine if tiles should be created.
 */
    static void performTilingAndSaveConfiguration(String modalityIndexFolder,
                                                  String imagingModality,
                                                  double frameWidth,
                                                  double frameHeight,
                                                  double overlapPercent,
                                                  List<Double> boundingBoxCoordinates = [],
                                                  boolean createTiles = true,
                                                  Collection<PathObject> annotations = []) {

        QP.mkdirs(modalityIndexFolder)
        boolean buffer = true
        if (boundingBoxCoordinates) {
            // Tiling logic when bounding box coordinates are provided
            def tilePath = QP.buildFilePath(modalityIndexFolder, "bounds")
            QP.mkdirs(tilePath)
            // Extract coordinates from the bounding box, all in microns
            double bBoxX = boundingBoxCoordinates[0]
            double bBoxY = boundingBoxCoordinates[1]
            double x2 = boundingBoxCoordinates[2]
            double y2 = boundingBoxCoordinates[3]
            double bBoxW = x2 - bBoxX
            double bBoxH = y2 - bBoxY
            //Create a half frame bounds around the area of interest
            if (buffer) {
                bBoxX = bBoxX - frameWidth / 2
                bBoxY = bBoxY - frameHeight/2
                //One extra full frame, since half frame on each side.
                bBoxH = bBoxH + frameHeight
                bBoxW = bBoxW + frameWidth

            }
            // Create an ROI for the bounding box
            def annotationROI = new RectangleROI(bBoxX, bBoxY, bBoxW, bBoxH, ImagePlane.getDefaultPlane())
            // Create tile configuration based on the bounding box, bBox value are in microns
            createTileConfiguration(bBoxX, bBoxY, bBoxW, bBoxH, frameWidth, frameHeight, overlapPercent, tilePath, annotationROI, imagingModality, createTiles)
        } else {
            // Tiling logic for existing annotations
            ImageData imageData = QPEx.getQuPath().getImageData()
            def hierarchy = imageData.getHierarchy()
            QP.clearDetections()
            // Retrieve all annotations

            // Naming each annotation based on its centroid coordinates
            annotations.each { annotation ->
                annotation.setName("${(int) annotation.getROI().getCentroidX()}_${(int) annotation.getROI().getCentroidY()}")
            }

            // Locking the annotations to prevent changes after processing
            QP.getAnnotationObjects().each { it.setLocked(true) }

            // Iterate over each annotation to create tile configuration
            annotations.each { annotation ->
                ROI annotationROI = annotation.getROI()
                double bBoxX = annotationROI.getBoundsX()
                double bBoxY = annotationROI.getBoundsY()
                double bBoxH = annotationROI.getBoundsHeight()
                double bBoxW = annotationROI.getBoundsWidth()
                String annotationName = annotation.getName()
                // Create folder for each annotation's tiles
                def tilePath = QP.buildFilePath(modalityIndexFolder, annotationName)
                QP.mkdirs(tilePath)
                //Create a half frame bounds around the area of interest
                if (buffer) {
                    bBoxX = bBoxX - frameWidth / 2
                    bBoxY = bBoxY - frameHeight/2
                    //One extra full frame, since half frame on each side.
                    bBoxH = bBoxH + frameHeight
                    bBoxW = bBoxW + frameWidth
                }
                // Create tile configuration for each annotation, bBox values are in pixels
                createTileConfiguration(bBoxX, bBoxY, bBoxW, bBoxH, frameWidth, frameHeight, overlapPercent, tilePath, annotationROI, imagingModality, createTiles)
            }
        }
    }

/**
 * Creates tile configuration for a given region of interest and saves it as a TileConfiguration.txt file.
 * This function either processes a specific ROI or the entire bounding box.
 *
 * @param bBoxX The X-coordinate of the top-left corner of the bounding box or ROI.
 * @param bBoxY The Y-coordinate of the top-left corner of the bounding box or ROI.
 * @param bBoxW The width of the bounding box or ROI.
 * @param bBoxH The height of the bounding box or ROI.
 * @param frameWidth The width of each tile.
 * @param frameHeight The height of each tile.
 * @param overlapPercent The percent overlap between tiles.
 * @param tilePath The path where the TileConfiguration.txt file will be saved.
 * @param annotationROI (Optional) The specific ROI to be tiled, null if tiling the entire bounding box.
 * @param imagingModality The type of imaging modality used, e.g., '4x-bf'.
 * @param createTiles Flag to determine if tile objects should be created in QuPath.
 */
    static void createTileConfiguration(double bBoxX,
                                        double bBoxY,
                                        double bBoxW,
                                        double bBoxH,
                                        double frameWidth,
                                        double frameHeight,
                                        double overlapPercent,
                                        String tilePath,
                                        ROI annotationROI,
                                        String imagingModality,
                                        boolean createTiles = true) {
        int predictedTileCount = 0
        int actualTileCount = 0
        List xy = []
        int yline = 0
        List newTiles = []
        double x = bBoxX
        double y = bBoxY
        // Calculate step size for X and Y based on frame size and overlap
        double xStep = frameWidth - (overlapPercent / 100 * frameWidth)
        double yStep = frameHeight - (overlapPercent / 100 * frameHeight)

        // Loop through Y-axis
        while (y < bBoxY + bBoxH) {
            // Loop through X-axis with conditional direction for serpentine tiling
            while ((x <= bBoxX + bBoxW) && (x >= bBoxX - bBoxW * overlapPercent / 100)) {
                def tileROI = new RectangleROI(x, y, frameWidth, frameHeight, ImagePlane.getDefaultPlane())
                // Check if tile intersects the given ROI or bounding box
                if (annotationROI == null || annotationROI.getGeometry().intersects(tileROI.getGeometry())) {
                    PathObject tileDetection = PathObjects.createDetectionObject(tileROI, QP.getPathClass(imagingModality))
                    tileDetection.setName(predictedTileCount.toString())
                    tileDetection.measurements.put("TileNumber", actualTileCount)
                    newTiles << tileDetection
                    xy << [x, y]
                    actualTileCount++
                }
                // Adjust X for next tile
                x = (yline % 2 == 0) ? x + xStep : x - xStep
                predictedTileCount++
            }
            // Adjust Y for next line and reset X
            y += yStep
            x = (yline % 2 == 0) ? x - xStep : x + xStep
            yline++
        }

        // Writing TileConfiguration.txt file
        String header = "dim = 2\n"
        new File(QP.buildFilePath(tilePath, "TileConfiguration.txt")).withWriter { fw ->
            fw.writeLine(header)
            xy.eachWithIndex { coords, index ->
                String line = "${index}.tif; ; (${coords[0]}, ${coords[1]})"
                fw.writeLine(line)
            }
        }
        // Add new tiles to the image if specified
        if (createTiles) {
            QP.getCurrentHierarchy().addObjects(newTiles)
            QP.fireHierarchyUpdate()
        }
    }

    //    static boolean checkValidAnnotationsGUI() {
//        Dialog<ButtonType> dlg = new Dialog<>()
//        dlg.initModality(Modality.NONE)
//        int annotationCount = QP.getAnnotationObjects().size()
//        dlg.setTitle("Validate annotation boundaries")
//        dlg.setHeaderText("There are $annotationCount Annotations in this image that will be processed. \n ADD, MODIFY or DELETE annotations to select regions to be scanned.")
//
//        // Define custom button types
//        ButtonType collectButton = new ButtonType("Collect $annotationCount regions", ButtonBar.ButtonData.OK_DONE);
//        ButtonType doNotCollectButton = new ButtonType("Do not collect ANY regions", ButtonBar.ButtonData.CANCEL_CLOSE);
//
//        // Add buttons to the dialog
//        dlg.getDialogPane().getButtonTypes().addAll(collectButton, doNotCollectButton)
//        Optional<ButtonType> result = dlg.showAndWait()
//        return result.isPresent() && result.get() == collectButton
//    }


//    static boolean checkValidAnnotationsGUI() {
//        // Ensure this runs on the JavaFX Application Thread
//        Platform.runLater(() -> {
//            Stage stage = new Stage();
//            stage.initModality(Modality.NONE);
//            stage.setTitle("Validate annotation boundaries");
//            stage.alwaysOnTop = true // Ensure the dialog stays on top
//
//            VBox layout = new VBox(10);
//            Label infoLabel = new Label("Checking annotations...");
//            Button collectButton = new Button("Collect regions");
//            Button doNotCollectButton = new Button("Do not collect ANY regions");
//
//            // Update button action
//            collectButton.setOnAction(e -> {
//                // Handle collect action
//
//                stage.close();
//                return true
//            });
//
//            doNotCollectButton.setOnAction(e -> {
//                // Handle do not collect action
//                logger.info("Do not collect, cancelled out of dialog.")
//                stage.close();
//                return false
//            });
//
//            layout.getChildren().addAll(infoLabel, collectButton, doNotCollectButton);
//            Scene scene = new Scene(layout, 400, 200);
//            stage.setScene(scene);
//
//            // Scheduled task to update the annotation count
//            var executor = Executors.newSingleThreadScheduledExecutor();
//            executor.scheduleAtFixedRate(() -> {
//                int annotationCount = QP.getAnnotationObjects().size(); // Implement this to get the current annotation count
//                Platform.runLater(() -> {
//                    infoLabel.setText("Total Annotation count in image to be processed: $annotationCount \nADD, MODIFY or DELETE annotations to select regions to be scanned.");
//                    collectButton.setText("Collect " + annotationCount + " regions");
//                });
//            }, 0, 500, TimeUnit.MILLISECONDS);
//
//            stage.setOnCloseRequest(e -> {
//                executor.shutdownNow(); // Ensure the executor is stopped when the stage closes
//            });
//
//            stage.showAndWait();
//        });
//    }

    static void checkValidAnnotationsGUI(Closure callback) {
        Platform.runLater(new Runnable() {
            void run() {
                Stage stage = new Stage()
                stage.initModality(Modality.NONE) // Non-blocking
                stage.title = "Validate annotation boundaries"
                stage.alwaysOnTop = true // Ensure the dialog stays on top

                VBox layout = new VBox(10)
                Label infoLabel = new Label("Checking annotations...")
                Button collectButton = new Button("Collect regions")
                Button doNotCollectButton = new Button("Do not collect ANY regions")

                collectButton.setOnAction({ e ->
                    logger.info( "Collect regions selected.")
                    stage.close()
                    callback.call(true)
                    // No explicit cast needed here
                })

                doNotCollectButton.setOnAction({ e ->
                    logger.info("Do not collect, cancelled out of dialog.")
                    stage.close()
                    callback.call(false)
                    // No explicit cast needed here
                })
                var executor = Executors.newSingleThreadScheduledExecutor();
                executor.scheduleAtFixedRate(() -> {
                    Platform.runLater(() -> {
                        // Assuming QP.getAnnotationObjects() is the correct method to retrieve the current annotations
                        // This might need to be adjusted based on your actual API for accessing annotations
                        int annotationCount = QP.getAnnotationObjects().findAll { it.getPathClass()
                                .toString().equals("Tissue") }.size();
                        infoLabel.setText("Total Annotation count in image to be processed: " + annotationCount +
                                "\nADD, MODIFY or DELETE annotations to select regions to be scanned." +
                                "\nEnsure that any newly created annotations are classified as 'Tissue'");
                        collectButton.setText("Collect " + annotationCount + " regions");
                    });
                }, 0, 500, TimeUnit.MILLISECONDS);


                layout.children.addAll(infoLabel, collectButton, doNotCollectButton)
                Scene scene = new Scene(layout, 400, 200)
                stage.scene = scene
                stage.show() // Non-blocking show
            }
        })
    }

}