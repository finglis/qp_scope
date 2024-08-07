package qupath.ext.qp_scope.utilities

import com.sun.javafx.collections.ObservableListWrapper
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
     * @param imagingModeWithIndex The scan type with an appended index for uniqueness, used in stitching process.
     * @param annotationName The name of the annotation, used as part of the new file name unless it is "bounds".
     * @param qupathGUI The QuPath GUI instance used for updating the project.
     * @param currentQuPathProject The current QuPath project to which the stitched image will be added.
     * @param compression The type of compression to use for stitching (default is "J2K_LOSSY").
     * @return The path to the renamed stitched image.
     */
    static String stitchImagesAndUpdateProject(String projectsFolderPath, String sampleLabel,
                                               String imagingModeWithIndex, String annotationName, QuPathGUI qupathGUI,
                                               Project currentQuPathProject,
                                               String compression = "J2K_LOSSY",
                                               double pixelSizeMicrons = 0,
                                               int downsample = 1) {

        String stitchedImageOutputFolder = projectsFolderPath + File.separator + sampleLabel + File.separator + "SlideImages"
        String tileImageInputFolder = projectsFolderPath + File.separator + sampleLabel + File.separator + imagingModeWithIndex

        logger.info("Calling stitchCore with $tileImageInputFolder")
        String stitchedImagePathStr = StitchingImplementations.stitchCore("Coordinates in TileConfiguration.txt file",
                tileImageInputFolder,
                stitchedImageOutputFolder,
                compression,
                pixelSizeMicrons,
                downsample,
                annotationName)

        File stitchedImagePath = new File(stitchedImagePathStr)
        String adjustedFileName = sampleLabel+ '_' + imagingModeWithIndex +  (annotationName.equals("bounds") ? "" : '_'+annotationName)+".ome.tif"
        File adjustedFilePath = new File(stitchedImagePath.parent, adjustedFileName)

        // Rename the stitched image file
        if (stitchedImagePath.renameTo(adjustedFilePath)) {
            stitchedImagePathStr = adjustedFilePath.absolutePath
        }

        Platform.runLater {
            logger.info("Platform.runLater section of stitchImagesAndUpdateProject")
            // Add the (possibly renamed) image to the project
            def preferences = QPEx.getQuPath().getPreferencePane().getPropertySheet().getItems()
            boolean invertedXAxis = preferences.find{it.getName() == "Inverted X stage"}.getValue() as Boolean
            boolean invertedYAxis = preferences.find{it.getName() == "Inverted Y stage"}.getValue() as Boolean
            QPProjectFunctions.addImageToProject(adjustedFilePath, currentQuPathProject,invertedXAxis,invertedYAxis )

            def matchingImage = currentQuPathProject.getImageList().find { image ->
                new File(image.getImageName()).name == adjustedFilePath.name
            }

            qupathGUI.openImageEntry(matchingImage)
            qupathGUI.setProject(currentQuPathProject)
            //TODO CHECK IMAGE METADATA FOR PIXEL SIZE IN PROJECT
            qupathGUI.refreshProject()
        }
        return stitchedImagePathStr
    }

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
 * @param pythonScriptPath The path to the Python script. This should be a directory if a specific script is not being passed.
 * @param arguments A list of arguments to pass to the Python script; can be null.
 * @param script The specific Python script to run, can be null to use default behavior based on arguments.
 * @return A List<String> with the output values if specific output handling is required; otherwise, null.
 */
    static List<String> runPythonCommand(String anacondaEnvPath, String pythonScriptPath, List<String> arguments, String script=null) {
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
            String scriptPath = script != null ? new File(new File(pythonScriptPath).getParent(), script).getCanonicalPath() : pythonScriptPath;
            logger.info("Running scriptPath $scriptPath");

            if (script == null){
                logger.info("Performing collection using $arguments");
                logger.info("Performing collection using $argsJoined");
                totalTifFiles = MinorFunctions.countTifEntriesInTileConfig(arguments);
                logger.info("File count will be $totalTifFiles")
                progressBar = true;
            }

            String command = String.format("\"%s\" -u \"%s\" %s", pythonExecutable, scriptPath, argsJoined);
            logger.info("Running Python Command as follows")
            logger.info("$command")
            Process process = Runtime.getRuntime().exec(command);
            //Only show progress bars for collections
            if (progressBar && totalTifFiles != 0) {
                logger.info("calling showProgressBar")
                UI_functions.showProgressBar(tifCount, totalTifFiles, process, 20000)
                //UI_functions.showProgressBar(tifCount, totalTifFiles)
            }

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            //This section handles the output from the python script, and what impact it has on the rest of the workflow
            Thread outputThread = new Thread(() -> {
                outputReader.lines().forEach(line -> {
                    //logger.info("Python: " + line);
                    if (line.contains("tiles done")) {

                        tifLines.add(line); // Add .tif line to the list
                        tifCount.incrementAndGet();
                        //logger.info("Line and $tifCount count")
                    } else if (line.contains("QuPath:")){
                        // Remove "QuPath: " from the line and then log it
                        String modifiedLine = line.replaceFirst("QuPath: ", "");
                        logger.info(modifiedLine);
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
                        } else if (errorLine.equals("Out of config")){
                            UI_functions.notifyUserOfError( errorLine, "Coordinates out of bounds")
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
 * @param imagingModalityWithIndex The type of imaging modality used, e.g., '4x_bf'.
 * @param frameWidth The width of each tile in either pixels (annotations) or microns (bounding box).
 * @param frameHeight The height of each tile in in either pixels (annotations) or microns (bounding box).
 * @param overlapPercent The percent overlap between adjacent tiles.
 * @param boundingBoxCoordinates The coordinates for the bounding box on the microscope stage in microns if provided, otherwise null.
 * @param createTiles Flag to determine if tiles should be created.
 */
    static void performTilingAndSaveConfiguration(String modalityIndexFolder,
                                                  String imagingModalityWithIndex,
                                                  double frameWidth,
                                                  double frameHeight,
                                                  double overlapPercent,
                                                  List<Double> boundingBoxCoordinates = [],
                                                  boolean createTiles = true,
                                                  Collection<PathObject> annotations = [],
                                                  boolean invertYAxis = false,
                                                  boolean invertXAxis = false) {

        QP.mkdirs(modalityIndexFolder)
        //Sets a half frame buffer around the imaging area
        boolean buffer = true
        logger.info(boundingBoxCoordinates.toString())
        //If bounding box coordinates are provided, use those instead of attempting an annotation based tiling
        if (boundingBoxCoordinates) {
//            // Tiling logic when bounding box coordinates are provided
//            logger.info("Use bounding box coordinates to create TileConfiguration.txt file")
//            def tilePath = QP.buildFilePath(modalityIndexFolder, "bounds")
//            QP.mkdirs(tilePath)
//            // Extract coordinates from the bounding box, all in microns
//            double bBoxX = boundingBoxCoordinates[0]
//            double bBoxY = boundingBoxCoordinates[1]
//            double x2 = boundingBoxCoordinates[2]
//            double y2 = boundingBoxCoordinates[3]
//            double bBoxW = x2 - bBoxX
//            double bBoxH = y2 - bBoxY
//            //Create a half frame bounds around the area of interest
//            if (buffer) {
//                bBoxX = bBoxX - frameWidth / 2
//                bBoxY = bBoxY - frameHeight/2
//                //One extra full frame, since half frame on each side.
//                bBoxH = bBoxH + frameHeight
//                bBoxW = bBoxW + frameWidth
//            }
//
//            // Create an ROI for the bounding box
//            def annotationROI = new RectangleROI(bBoxX, bBoxY, bBoxW, bBoxH, ImagePlane.getDefaultPlane())
//            // Create tile configuration based on the bounding box, bBox value are in microns
//            tilePath = QP.buildFilePath(tilePath, "TileConfiguration.txt")
//            createTileConfiguration(bBoxX, bBoxY, bBoxW, bBoxH, frameWidth, frameHeight, overlapPercent, tilePath, annotationROI, imagingModalityWithIndex, createTiles)
            logger.info("Using bounding box coordinates to create TileConfiguration.txt file")
            def tilePath = QP.buildFilePath(modalityIndexFolder, "bounds")
            QP.mkdirs(tilePath)

            // Extract coordinates from the bounding box, all in microns
            double x1 = boundingBoxCoordinates[0]
            double y1 = boundingBoxCoordinates[1]
            double x2 = boundingBoxCoordinates[2]
            double y2 = boundingBoxCoordinates[3]

            // Calculate top-left and bottom-right coordinates based on min/max
            double startX = Math.min(x1, x2)
            double endX = Math.max(x1, x2)
            double startY = Math.min(y1, y2)
            double endY = Math.max(y1, y2)
            //logger.info("Calculated min and max coordinates: minX={}, maxX={}, minY={}, maxY={}", minX, maxX, minY, maxY)

            // Apply inversion logic
//            double startX = invertXAxis ? minX : maxX;
//            double endX = invertXAxis ? maxX : minX;
//            double startY = invertYAxis ? minY : maxY;
//            double endY = invertYAxis ? maxY : minY;
//            logger.info("Adjusted coordinates after applying axis inversion: startX={}, endX={}, startY={}, endY={}", startX, endX, startY, endY)

            // Adjust coordinates for buffering and ensure positive dimensions
            startX -= frameWidth / 2;
            startY -= frameHeight / 2;
            double width = Math.abs((endX - startX)) + frameWidth;
            double height = Math.abs((endY - startY)) + frameHeight;
            logger.info("Final bounding box dimensions after buffering: startX={}, startY={}, width={}, height={}", startX, startY, width, height)

            def annotationROI = new RectangleROI(startX, startY, width, height, ImagePlane.getDefaultPlane())
            tilePath = QP.buildFilePath(tilePath, "TileConfiguration.txt")
            createTileConfiguration(startX, startY, width, height, frameWidth, frameHeight, overlapPercent,
                    tilePath, annotationROI, imagingModalityWithIndex, createTiles)
            logger.info("Tile configuration created at {}", tilePath)

        } else {
            // Tiling logic for existing annotations

            //Remove the indexing from the modality.
            def imagingModality = imagingModalityWithIndex.replaceAll(/(_\d+)$/, "")
            logger.info("Removing old tiles that class match $imagingModality")
            //TODO check for closure error
            def relevantTiles = QP.getDetectionObjects().findAll{it.getPathClass().toString().toLowerCase().contains(imagingModality)}
            logger.info("Removing ${relevantTiles.size()} tiles")
            QP.removeObjects(relevantTiles, true)
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
                //Create a half frame bounds around the area of interest to avoid cutting off bits of tissue
                if (buffer) {
                    bBoxX = bBoxX - frameWidth/2
                    bBoxY = bBoxY - frameHeight/2
                    //One extra full frame, since half frame on each side
                    bBoxH = bBoxH + frameHeight
                    bBoxW = bBoxW + frameWidth
                }
                // Create tile configuration for each annotation, bBox and frame values are in QuPath image pixels
                // A different TileConfiguration_QP file name is useful here to distinguish the QuPath coordinate system from the stage coordinate system
                tilePath = QP.buildFilePath(tilePath, "TileConfiguration_QP.txt")
                createTileConfiguration(bBoxX, bBoxY, bBoxW, bBoxH, frameWidth, frameHeight, overlapPercent, tilePath, annotationROI, imagingModality, createTiles)
            }
        }
    }

/**
 * Creates tile configuration for a given region of interest and saves it as a TileConfiguration_QP.txt file.
 * This function either processes a specific ROI or the entire bounding box.
 * Each entry in the TileConfiguration_QP.txt file represents the CENTROID of a QuPath based tile.
 *
 * @param bBoxX The X-coordinate of the top-left corner of the bounding box or ROI.
 * @param bBoxY The Y-coordinate of the top-left corner of the bounding box or ROI.
 * @param bBoxW The width of the bounding box or ROI.
 * @param bBoxH The height of the bounding box or ROI.
 * @param frameWidth The width of each tile.
 * @param frameHeight The height of each tile.
 * @param overlapPercent The percent overlap between tiles.
 * @param tilePath The path where the TileConfiguration_QP.txt file will be saved.
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
                                        boolean createTiles = true,
                                        boolean invertYAxis = false,
                                        boolean invertXAxis = false) {
        logger.info("TileConfig setup")
        int predictedTileCount = 0
        int actualTileCount = 0
        List<Double[]> xy = []
        int yline = 0
        List newTiles = []

        logger.info("frameHeight: $frameHeight")
        logger.info("frameWidth: $frameWidth")
        // Calculate step size for X and Y based on frame size and overlap
        double xStep = frameWidth - (overlapPercent / 100 * frameWidth)
        double yStep = frameHeight - (overlapPercent / 100 * frameHeight)

//        double startX = invertXAxis ? bBoxX + bBoxW : bBoxX;
//        double endX = invertXAxis ? bBoxX : bBoxX + bBoxW;
//        double startY = invertYAxis ? bBoxY + bBoxH : bBoxY;
//        double endY = invertYAxis ? bBoxY : bBoxY + bBoxH;


        double y = bBoxY;
        double x = bBoxX
         //Loop through Y-axis
        while (y < bBoxY + bBoxH) {
            // Loop through X-axis with conditional direction for serpentine tiling
            // While this doesn't matter from QuPath's perspective, it can speed up collection when micromanager acquires the tiles in this order
            while ((x <= bBoxX + bBoxW) && (x >= bBoxX - bBoxW * overlapPercent / 100)) {
                def tileROI = new RectangleROI(x, y, frameWidth, frameHeight, ImagePlane.getDefaultPlane())
                // Check if tile intersects the given ROI or bounding box (null)
                if (annotationROI == null || annotationROI.getGeometry().intersects(tileROI.getGeometry())) {
                    PathObject tileDetection = PathObjects.createDetectionObject(tileROI, QP.getPathClass(imagingModality))
                    tileDetection.setName(predictedTileCount.toString())
                    tileDetection.measurements.put("TileNumber", actualTileCount)
                    newTiles << tileDetection
                    //Adjusting to middle of frame rather than upper left
                    //xy << [x, y]
                    xy << [tileROI.getCentroidX(), tileROI.getCentroidY()]
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

        // Writing TileConfiguration_QP.txt file
        String header = "dim = 2\n"
        logger.info("Writing out file to $tilePath")
        logger.info("Predicted tile count $predictedTileCount")
        logger.info("Actual tile count $actualTileCount")
        new File(tilePath).withWriter { fw ->
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

    static void moveStageToSelectedTile() {
        List selectedObjects = QP.getSelectedObjects().stream()
                .filter(object -> object.isDetection() && object.getROI() instanceof qupath.lib.roi.RectangleROI)
                .collect(Collectors.toList());

        if (selectedObjects.size() != 1) {
            MinorFunctions.showAlertDialog("There needs to be exactly one tile selected.");
            return; // Keep dialog open
        }
        //Get the position of the current object
        ROI tileROI = QP.getSelectedObjects()[0].getROI()
        def tileLocation = [tileROI.getBoundsX(), tileROI.getBoundsY()]
        //Get the image name
        def imageName = QP.getCurrentServerPath()
        //Get the _StageCoordinates.txt file for the image
        def uriList = QP.getCurrentServer().getURIs()
        def uri = uriList[0].toString() // Assuming there's only one URI for simplicity

        // Convert URI to a decoded file path
        def decodedPath = URLDecoder.decode(uri, "UTF-8").replace("file:/", "")
        //Find the bounding box of the image in stage coordinates
        def stageBounds = MinorFunctions.readTileExtremesFromFile(decodedPath)
        def imageWidth=QP.getCurrentServer().getMetadata().getWidth()
        def imageHeight= QP.getCurrentServer().getMetadata().getHeight()

        //Interpolate the position of the current tile with the image bounds
        // Calculate scaling factors based on stage and image dimensions
        double scaleX = (stageBounds[1][0] - stageBounds[0][0]) / imageWidth
        double scaleY = (stageBounds[1][1] - stageBounds[0][1]) / imageHeight

        // Calculate the stage coordinates of the tile
        double tileStageX = stageBounds[0][0] + tileLocation[0] * scaleX
        double tileStageY = stageBounds[0][1] + tileLocation[1] * scaleY
        def expectedStageXYPositionMicrons = [tileStageX, tileStageY] as List<String>

        def preferences = QPEx.getQuPath().getPreferencePane().getPropertySheet().getItems()
        String virtualEnvPath = preferences.find { it.getName() == "Python Environment" }.getValue() as String
        String pythonScriptPath = preferences.find { it.getName() == "PycroManager Path" }.getValue() as String
        runPythonCommand(virtualEnvPath, pythonScriptPath, expectedStageXYPositionMicrons, "moveStageToCoordinates.py")
        logger.info("Moving stage to selected tile...");

    }



}