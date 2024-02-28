package qupath.ext.qp_scope.ui

import com.sun.javafx.collections.ObservableListWrapper
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.stage.Modality
import org.slf4j.LoggerFactory
import qupath.ext.qp_scope.utilities.QPProjectFunctions
import qupath.ext.qp_scope.utilities.UtilityFunctions
import qupath.ext.qp_scope.utilities.MinorFunctions
import qupath.ext.qp_scope.utilities.TransformationFunctions
import qupath.ext.qp_scope.ui.UI_functions
import qupath.lib.gui.QuPathGUI
import qupath.fx.dialogs.Dialogs
import qupath.lib.gui.scripting.QPEx
import qupath.lib.objects.PathObject
import qupath.lib.projects.Project
import qupath.lib.scripting.QP

import javafx.stage.Window
import java.awt.event.ActionEvent
import java.awt.geom.Point2D
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

import javafx.stage.Modality
import javafx.stage.Stage


//Thoughts:
//Have collapsible sections to a larger dialog box?
//        Alternatively, individual dialogs for each step, but have menu options for each as well. The menu options for steps not yet reached would need to be greyed out.

class QP_scope_GUI {
    static final logger = LoggerFactory.getLogger(QP_scope_GUI.class)
    // GUI elements
    static TextField x1Field = new TextField("")
    static TextField y1Field = new TextField("")
    static TextField x2Field = new TextField("")
    static TextField y2Field = new TextField("")
    static TextField scanBox = new TextField("-13316,-1580,-14854,-8474")
    static preferences = QPEx.getQuPath().getPreferencePane().getPropertySheet().getItems()
    static TextField sampleLabelField = new TextField("First_Test")
    static TextField classFilterField = new TextField("Tumor, Immune, PDAC")
    static def extensionPath = preferences.find{it.getName() == "Extension Location"}.getValue().toString()
    static TextField groovyScriptField = new TextField(extensionPath+"/src/main/groovyScripts/DetectTissue.groovy")

    static TextField pixelSizeField = new TextField(preferences.find{it.getName() == "Macro image px size"}.getValue().toString())
    static CheckBox nonIsotropicCheckBox = new CheckBox("Non-isotropic pixels")



        /**********************************
     * Starting point for an overview or "macro" image
     */

    static void testGUI() {
        // Create the dialog
//        def dlg = new Dialog<ButtonType>()
//        dlg.initModality(Modality.APPLICATION_MODAL)
//        dlg.setTitle("qp_scope")
//        //dlg.setHeaderText("Enter details (LOOK MA! " + BasicStitchingExtension.class.getName() + "!):");
//        dlg.setOnShown(event -> {
//            Window window = dlg.getDialogPane().getScene().getWindow();
//            if (window instanceof Stage) {
//                ((Stage) window).setAlwaysOnTop(true);
//            }
//        });
//        // Set the content
//        dlg.getDialogPane().setContent(createBoundingBoxInputGUI())
//
//        // Add Okay and Cancel buttons
//        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL)


        AtomicInteger progressCounter = new AtomicInteger(0);
        int totalFiles = 5

        UI_functions.showProgressBar(progressCounter, totalFiles)
        new Thread(() -> {
            while (progressCounter.get() < totalFiles) {
                logger.info(Integer.toString(progressCounter.get()));
                try {
                    Thread.sleep(1000); // Sleep for 1 second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    logger.error("Thread interrupted", e);
                }
                progressCounter.incrementAndGet();
            }
        }).start();


        // Preferences from GUI
        double frameWidth = preferences.find{it.getName() == "Camera Frame Width #px"}.getValue() as Double
        double frameHeight = preferences.find{it.getName() == "Camera Frame Height #px"}.getValue() as Double
        double pixelSizeSource = preferences.find{it.getName() == "Macro image px size"}.getValue() as Double
        double pixelSizeFirstScanType = preferences.find{it.getName() == "1st scan pixel size um"}.getValue() as Double
        double overlapPercent = preferences.find{it.getName() == "Tile Overlap Percent"}.getValue() as Double
        String projectsFolderPath = preferences.find{it.getName() == "Projects Folder"}.getValue() as String
        String virtualEnvPath =  preferences.find{it.getName() == "Python Environment"}.getValue() as String
        String pythonScriptPath =  preferences.find{it.getName() == "PycroManager Path"}.getValue() as String
        String compressionType = preferences.find{it.getName() == "Compression type"}.getValue() as String
        String tileHandling = preferences.find{it.getName() == "Tile Handling Method"}.getValue() as String
        boolean isSlideFlippedX = preferences.find{it.getName() == "Flip macro image X"}.getValue() as Boolean
        boolean isSlideFlippedY = preferences.find{it.getName() == "Flip macro image Y"}.getValue() as Boolean

        List<String> args = [projectsFolderPath,
                             "First_Test",
                             "4x_bf_1",
                             "2220_1738",
        ] as List<String>
        int count = MinorFunctions.countTifEntriesInTileConfig(args)
        logger.info("Count is $count")

        // Show the dialog and capture the response
//        def result = dlg.showAndWait()
//
//        // Handling the response
//        if (result.isPresent() && result.get() == ButtonType.OK) {
//            // Retrieve values from text fields
//            def sampleLabel = sampleLabelField.getText()
//
//            def x1 = x1Field.getText()
//            def y1 = y1Field.getText()
//            def x2 = x2Field.getText()
//            def y2 = y2Field.getText()
//            // Handle full bounding box input
//            def boxString = scanBox.getText()
//            //Boolean to check whether to proceed with running the microscope data collection
//            boolean dataCheck = true
//            def pixelSize = preferences.find{it.getName() == "Pixel Size Source"}.getValue().toString()
//
//            // Continue with previous behavior using coordinates
//
//            if (boxString != "") {
//                def values = boxString.replaceAll("[^0-9.,]", "").split(",")
//                if (values.length == 4) {
//                    x1 = values[0]
//                    y1 = values[1]
//                    x2 = values[2]
//                    y2 = values[3]
//                }
//            }
//            if ([sampleLabel, x1, y1, x2, y2, virtualEnvPath, pythonScriptPath].any { it == null || it.isEmpty() }) {
//                Dialogs.showWarningNotification("Warning!", "Incomplete data entered.")
//                dataCheck = false
//            }
//
//
//            // Check if any value is empty
//
//            if (dataCheck) {
//                QuPathGUI qupathGUI = QPEx.getQuPath()
//
//                def qp_test_coords_1 = [2216.9667073567707, 1094.4444580078125]
//                def stage_test_coords_1 = [-11797.03, -1374.9]
//                def qp_test_coords_2 = [2003.3333740234375, 1573.277791341146]
//                def stage_test_coords_2  = [-13371, -4819.9]
//                def qp_test_coords_3 = [2110.150040690104, 1972.3055691189236]
//                def stage_test_coords_3  = [-11873.289, -8163.269]
//                def qp_test_coords_4 = [1896.516707356771, 1972.3055691189236]
//                def stage_test_coords_4  = [-11856.86, -8028.46]
//                def qp_test_list = [qp_test_coords_1,qp_test_coords_2, qp_test_coords_3, qp_test_coords_4]
//                def stage_test_list = [stage_test_coords_1,stage_test_coords_2, stage_test_coords_3, stage_test_coords_4]
//
//                for (int i = 0; i < qp_test_list.size(); i++) {
//                    List<Double> qpTestCoords = qp_test_list[i]
//                    List<Double> stageTestCoords = stage_test_list[i]
//
//                    // Create initial scaling transform
//                    AffineTransform transformation = new AffineTransform()
//                    double scale =  (pixelSize as Double)/ (preferences.find{it.getName() == "Pixel Size for First Scan Type"}.getValue() as Double)
//                    logger.info("scale is $scale")
//                    transformation.scale(scale, -scale)
//                    logger.info("transformation at this point should be 0.15, 0,0  0, 0.15, 0: $transformation")
//                    // Calculate the transformation for the current pair
//                    transformation = TransformationFunctions.addTranslationToScaledAffine(transformation, qpTestCoords.collect { it.toString() }, stageTestCoords.collect { it.toString() })
//                    logger.info("Transformation for pair ${i + 1}: $transformation")
//
//                    // Apply the transformation to each test coordinate
//                    qp_test_list.each { qpCoords ->
//                        Point2D.Double transformedPoint = applyTransformation(transformation, qpCoords as double[])
//                        logger.info("Converted $qpCoords to $transformedPoint")
//                        logger.info("Expected value was: ${stage_test_list[qp_test_list.indexOf(qpCoords)]}")
//                    }
//                }
//
//
//            }
//        }
    }
/**
 * Launches a graphical user interface for macro image input in a microscopy imaging workflow. This function facilitates
 * the acquisition of necessary parameters from the user, such as coordinates, preferences, and paths for scripts and
 * environment setups. It validates user input, retrieves application preferences, executes scripts for tissue detection,
 * and manages tile configurations and stitching tasks. The GUI also handles affine transformation setup for image alignment
 * and coordinates the execution of Python scripts for microscope control and image processing.
 */
    static void macroImageInputGUI() {
        // Create the dialog
        def dlg = createMacroImageInputDialog()

        // Define response validation
        dlg.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (!UI_functions.isValidInput(x1Field.getText()) || !UI_functions.isValidInput(y1Field.getText())) {
                    Dialogs.showWarningNotification("Invalid Input", "Please enter valid numeric values for coordinates.")
                    return null // Prevent dialog from closing
                }
            }
            return dialogButton
        })

        // Show the dialog and capture the response
        Optional<ButtonType> result = dlg.showAndWait()

        // Handling the response
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Retrieve values from text fields and checkbox

            //TODO implement separate xy processing

            boolean arePixelsNonIsotropic = nonIsotropicCheckBox.isSelected()
            String groovyScriptPath = groovyScriptField.getText()
            def sampleLabel = sampleLabelField.getText()

            // Preferences from GUI
            double frameWidth = preferences.find{it.getName() == "Camera Frame Width #px"}.getValue() as Double
            double frameHeight = preferences.find{it.getName() == "Camera Frame Height #px"}.getValue() as Double
            double pixelSizeSource = preferences.find{it.getName() == "Macro image px size"}.getValue() as Double
            double pixelSizeFirstScanType = preferences.find{it.getName() == "1st scan pixel size um"}.getValue() as Double
            double overlapPercent = preferences.find{it.getName() == "Tile Overlap Percent"}.getValue() as Double
            String projectsFolderPath = preferences.find{it.getName() == "Projects Folder"}.getValue() as String
            String virtualEnvPath =  preferences.find{it.getName() == "Python Environment"}.getValue() as String
            String pythonScriptPath =  preferences.find{it.getName() == "PycroManager Path"}.getValue() as String
            String compressionType = preferences.find{it.getName() == "Compression type"}.getValue() as String
            String tileHandling = preferences.find{it.getName() == "Tile Handling Method"}.getValue() as String
            boolean isSlideFlippedX = preferences.find{it.getName() == "Flip macro image X"}.getValue() as Boolean
            boolean isSlideFlippedY = preferences.find{it.getName() == "Flip macro image Y"}.getValue() as Boolean

// Log retrieved preference values
            logger.info("frameWidth: $frameWidth")
            logger.info("frameHeight: $frameHeight")
            logger.info("pixelSizeSource: $pixelSizeSource")
            logger.info("pixelSizeFirstScanType: $pixelSizeFirstScanType")
            logger.info("overlapPercent: $overlapPercent")
            logger.info("projectsFolderPath: $projectsFolderPath")
            logger.info("virtualEnvPath: $virtualEnvPath")
            logger.info("pythonScriptPath: $pythonScriptPath")
            logger.info("compressionType: $compressionType")

            // Check if data is all present
            if ([groovyScriptPath, projectsFolderPath, virtualEnvPath, pythonScriptPath, compressionType].any { it == null || it.isEmpty() }) {
                Dialogs.showWarningNotification("Warning!", "Insufficient data to send command to microscope!")
                return
            }
            //String imageName = QP.getCurrentImageName()

            Map scriptPaths = MinorFunctions.calculateScriptPaths(groovyScriptPath)
            String jsonTissueClassifierPathString = scriptPaths.jsonTissueClassfierPathString
            QuPathGUI qupathGUI = QPEx.getQuPath()
            Map projectDetails
            // Log input variables before entering the if-else block
            logger.info("Checking inputs before creating or retrieving project information:");
            logger.info("qupathGUI: " + qupathGUI);
            logger.info("projectsFolderPath: " + projectsFolderPath);
            logger.info("sampleLabel: " + sampleLabel);
            //create a projectDetails map with four values that will be needed later, all related to project creation.
            if (QPEx.getProject() == null) {
                projectDetails = QPProjectFunctions.createAndOpenQuPathProject(qupathGUI, projectsFolderPath, sampleLabel,
                        preferences as ObservableListWrapper, isSlideFlippedX, isSlideFlippedY)
            }else{
                //If the project already exists and an image is open, return that information
                projectDetails = QPProjectFunctions.getCurrentProjectInformation(projectsFolderPath, sampleLabel, preferences as ObservableListWrapper)
            }
            Project currentQuPathProject = projectDetails.currentQuPathProject as Project
            String tempTileDirectory = projectDetails.tempTileDirectory


            String tissueDetectScript = UtilityFunctions.modifyTissueDetectScript(groovyScriptPath, pixelSizeSource as String, jsonTissueClassifierPathString)
            //logger.info(tissueDetectScript)
            // Run the modified script
            QuPathGUI.getInstance().runScript(null, tissueDetectScript)
            //At this point the tissue should be outlined in an annotation



            def annotations = QP.getAnnotationObjects().findAll { it.getPathClass().toString().equals("Tissue") }
            Double frameWidthMicrons = (frameWidth) / (pixelSizeSource) * (pixelSizeFirstScanType)
            Double frameHeightMicrons = (frameHeight) / (pixelSizeSource) * (pixelSizeFirstScanType)
            UtilityFunctions.performTilingAndSaveConfiguration(tempTileDirectory,
                    projectDetails.scanTypeWithIndex.toString(),
                    frameWidthMicrons,
                    frameHeightMicrons,
                    overlapPercent,
                    null,
                    true,
                    annotations)

            //create a basic affine transformation, add the scaling information and a possible Y axis flip
            //Then create a dialog that asks the user to select a single detection tile
            AffineTransform transformation = TransformationFunctions.setupAffineTransformationAndValidationGUI(pixelSizeSource as Double, preferences as ObservableListWrapper)
            logger.info("Initial affine transform, scaling only: $transformation")
            //If user exited out of the dialog, the transformation should be null, and we do not want to continue.
            if (transformation == null) {
                return
            }

            PathObject expectedTile = QP.getSelectedObject()
            def detections = QP.getDetectionObjects()
            def topCenterTileXY = TransformationFunctions.getTopCenterTile(detections)
            def leftCenterTileXY = TransformationFunctions.getLeftCenterTile(detections)


            // Get the current stage coordinates to figure out the translation from the first alignment.
            List coordinatesQP = [expectedTile.getROI().getBoundsX(), expectedTile.getROI().getBoundsY()] as List<Double>
            if (!coordinatesQP) {
                logger.error("Need coordinates.")
                return
            }
            logger.info("user adjusted position of tile at $coordinatesQP")
            List<String> currentStageCoordinates_um_String = UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, null)
            logger.info("Obtained stage coordinates: $currentStageCoordinates_um_String")
            logger.info("QuPath coordinates for selected tile: $coordinatesQP")
            logger.info("affine transform before initial alignment: $transformation")
            List<Double> currentStageCoordinates_um = MinorFunctions.convertListToDouble(currentStageCoordinates_um_String)
            //TODO TEST THIS
            // Calculate the offset in microns - the size of one frame in stage coordinates
            double offsetX = -1*frameWidth * pixelSizeFirstScanType;
            double offsetY = -1*frameHeight * pixelSizeFirstScanType;
            // Create the offset AffineTransform
            AffineTransform offset = new AffineTransform();
            offset.translate(offsetX, offsetY);
            transformation = TransformationFunctions.addTranslationToScaledAffine(transformation, coordinatesQP, currentStageCoordinates_um, offset)
            logger.info("affine transform after initial alignment: $transformation")


            // Handle stage alignment for top center tile
            Map resultsTopCenter = handleStageAlignment(topCenterTileXY, qupathGUI, virtualEnvPath, pythonScriptPath, transformation)
            if (!resultsTopCenter.updatePosition) {
                logger.info("Window was closed, alignment cancelled.")
                return // Exit if position validation fails
            }
            transformation = resultsTopCenter.transformation as AffineTransform

            // Handle stage alignment for left center tile
            Map resultsLeftCenter = handleStageAlignment(leftCenterTileXY, qupathGUI, virtualEnvPath, pythonScriptPath, transformation)
            if (!resultsLeftCenter.updatePosition) {
                logger.info("Window was closed, alignment cancelled.")
                return // Exit if position validation fails
            }
            transformation = resultsLeftCenter.transformation as AffineTransform

            //The TileConfiguration.txt file created by the Groovy script is in QuPath pixel coordinates.
            //It must be transformed into stage coordinates in microns
            logger.info("export script path string $tempTileDirectory")
            def tileconfigFolders = TransformationFunctions.transformTileConfiguration(tempTileDirectory, transformation)
            for (folder in tileconfigFolders) {
                logger.info("modified TileConfiguration at $folder")
            }

            Semaphore pythonCommandSemaphore = new Semaphore(1);
            annotations.each { annotation ->

                List<String> args = [projectsFolderPath,
                                     sampleLabel,
                                     projectDetails.scanTypeWithIndex,
                                     annotation.getName(),
                ] as List<String>
                logger.info("Check input args for runPythonCommand")

                CompletableFuture<List<String>> pythonFuture = runPythonCommandAsync(virtualEnvPath, pythonScriptPath, args, pythonCommandSemaphore);


// Handle the successful completion of the Python command
                pythonFuture.thenAcceptAsync(stageCoordinates -> {
                    // Process the result for successful execution
                    logger.info("Begin stitching")
                    String stitchedImagePathStr = UtilityFunctions.stitchImagesAndUpdateProject(
                            projectsFolderPath,
                            sampleLabel,
                            projectDetails.scanTypeWithIndex as String,
                            annotation.getName(),
                            qupathGUI,
                            currentQuPathProject,
                            compressionType);
                    logger.info("Stitching completed at $stitchedImagePathStr")
                    // Ensure stitching operation is also non-blocking and async
                }).exceptionally(throwable -> {
                    // Handle any exceptions from the Python command
                    logger.error("Error during Python script execution: ${throwable.message}")
                    UI_functions.notifyUserOfError("Error during Python script execution: ${throwable.message}", "Python Script Execution")
                    return null; // To comply with the Function interface return type
                });
//                pythonFuture.whenComplete((output, error) -> {
//                    logger.info("Begin stitching")
//                    // Assuming stageCoordinates are used in stitching; adjust as necessary
//                    String stitchedImagePathStr = UtilityFunctions.stitchImagesAndUpdateProject(
//                            projectsFolderPath,
//                            sampleLabel,
//                            projectDetails.scanTypeWithIndex as String,
//                            annotation.getName(),
//                            qupathGUI,
//                            currentQuPathProject,
//                            compressionType);
//                logger.info("Stitching completed at $stitchedImagePathStr")
//                });
            }

            logger.info("All collections complete, tiles will be handled as $tileHandling")
            //Check if the tiles should be deleted from the collection folder set
            //tempTileDirectory is the parent folder to each annotation/bounding folder

            if (tileHandling == "Delete")
                UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
            if (tileHandling == "Zip") {
                UtilityFunctions.zipTilesAndMove(tempTileDirectory)
                UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
            }

            logger.info("check2")
        }
        logger.info("check3")
    }

    /**
     * Initiates a graphical user interface for inputting a bounding box to create a tiling grid.
     * The function collects inputs from the user regarding the bounding box coordinates or a full bounding box specification.
     * It then processes these inputs to set up and execute a microscopy scan based on the specified area.
     * This includes creating a QuPath project, configuring tiling parameters, running a Python script for data collection,
     * and optionally handling the stitched image and tile management post-scanning.
     */
    static void boundingBoxInputGUI() {
        // Create the dialog
        def dlg = new Dialog<ButtonType>()
        dlg.initModality(Modality.APPLICATION_MODAL)
        dlg.setOnShown(event -> {
            Window window = dlg.getDialogPane().getScene().getWindow();
            if (window instanceof Stage) {
                ((Stage) window).setAlwaysOnTop(true);
            }
        });
        dlg.setTitle("qp_scope")
        //dlg.setHeaderText("Enter details (LOOK MA! " + BasicStitchingExtension.class.getName() + "!):");

        // Set the content
        dlg.getDialogPane().setContent(createBoundingBoxInputGUI())

        // Add Okay and Cancel buttons
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL)

        // Show the dialog and capture the response
        def result = dlg.showAndWait()

        // Handling the response
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Retrieve values from text fields
            def sampleLabel = sampleLabelField.getText()
            def x1 = x1Field.getText()
            def y1 = y1Field.getText()
            def x2 = x2Field.getText()
            def y2 = y2Field.getText()
            // Handle full bounding box input
            def boxString = scanBox.getText()
            //Boolean to check whether to proceed with running the microscope data collection
            boolean dataCheck = true

            //Preferences from GUI
            double frameWidth = preferences.find{it.getName() == "Camera Frame Width #px"}.getValue() as Double
            double frameHeight = preferences.find{it.getName() == "Camera Frame Height #px"}.getValue() as Double
            double pixelSizeFirstScanType = preferences.find{it.getName() == "1st scan pixel size um"}.getValue() as Double
            double overlapPercent = preferences.find{it.getName() == "Tile Overlap Percent"}.getValue() as Double
            String projectsFolderPath = preferences.find{it.getName() == "Projects Folder"}.getValue() as String
            String virtualEnvPath =  preferences.find{it.getName() == "Python Environment"}.getValue() as String
            String pythonScriptPath =  preferences.find{it.getName() == "PycroManager Path"}.getValue() as String
            String compressionType = preferences.find{it.getName() == "Compression type"}.getValue() as String
            String tileHandling = preferences.find{it.getName() == "Tile Handling Method"}.getValue() as String

            // Continue with previous behavior using coordinates
            if (boxString != "") {
                def values = boxString.replaceAll("[^0-9.,]", "").split(",")
                if (values.length == 4) {
                    x1 = values[0]
                    y1 = values[1]
                    x2 = values[2]
                    y2 = values[3]
                }
            }

            if ([sampleLabel, x1, y1, x2, y2, virtualEnvPath, pythonScriptPath].any { it == null || it.isEmpty() }) {
                Dialogs.showWarningNotification("Warning!", "Incomplete data entered.")
                dataCheck = false
            }


            // Check if any value is empty
            if (dataCheck) {
                QuPathGUI qupathGUI = QPEx.getQuPath()
                Map projectDetails = QPProjectFunctions.createAndOpenQuPathProject(qupathGUI, projectsFolderPath, sampleLabel, preferences as ObservableListWrapper)
                Project currentQuPathProject = projectDetails.currentQuPathProject as Project
                String tempTileDirectory = projectDetails.tempTileDirectory
                String scanTypeWithIndex = projectDetails.scanTypeWithIndex

                //Specifically for the case where there is only a bounding box provided
                List<Double> boundingBoxValues = [x1, y1, x2, y2].collect { it.toDouble() }
                Double frameWidthMicrons = (frameWidth ) * (pixelSizeFirstScanType )
                Double frameHeightMicrons = (frameHeight )  * (pixelSizeFirstScanType )
                UtilityFunctions.performTilingAndSaveConfiguration(tempTileDirectory, scanTypeWithIndex,
                        frameWidthMicrons,
                        frameHeightMicrons,
                        overlapPercent,
                        boundingBoxValues,
                false)

                //Send the scanning command to the microscope

                List args = [projectsFolderPath,
                             sampleLabel,
                             scanTypeWithIndex,
                             "bounds"]
                //TODO can we create non-blocking python code
                UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, args)

                // Handle image stitching and update project

                String stitchedImagePathStr = UtilityFunctions.stitchImagesAndUpdateProject(projectsFolderPath,
                        sampleLabel, scanTypeWithIndex, "bounds", qupathGUI, currentQuPathProject,
                        compressionType)
                logger.info(stitchedImagePathStr)

                qupathGUI.refreshProject()
                //Check if the tiles should be deleted from the collection folder

                if (tileHandling == "Delete")
                    UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
                if (tileHandling == "Zip") {
                    UtilityFunctions.zipTilesAndMove(tempTileDirectory)
                    UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
                }
                //}
            }
        }
    }
/**
 * Creates and returns a GridPane containing input fields for specifying a bounding box.
 * This includes fields for entering the coordinates of the top-left (X1, Y1) and bottom-right (X2, Y2) corners of the bounding box,
 * as well as an option for entering a full bounding box specification. It is designed to facilitate user input in the boundingBoxInputGUI function.
 *
 * @return GridPane with labeled input fields for bounding box specification.
 */
    private static GridPane createBoundingBoxInputGUI() {
        GridPane pane = new GridPane()
        pane.setHgap(10)
        pane.setVgap(10)
        def row = 0

        // Add new component for Sample Label
        UI_functions.addToGrid(pane, new Label('Sample Label:'), sampleLabelField, row++)

        // Add existing components to the grid
        UI_functions.addToGrid(pane, new Label('X1:'), x1Field, row++)
        UI_functions.addToGrid(pane, new Label('Y1:'), y1Field, row++)
        UI_functions.addToGrid(pane, new Label('X2:'), x2Field, row++)
        UI_functions.addToGrid(pane, new Label('Y2:'), y2Field, row++)
        UI_functions.addToGrid(pane, new Label('Full bounding box:'), scanBox, row++)

        return pane
    }

/**
 * Launches a GUI dialog for initiating a secondary modality data collection process within selected annotations of the current image.
 * The function gathers user input from the GUI to specify parameters for the collection, such as sample label and class filters for annotations.
 * It then calculates necessary parameters based on user preferences and initiates the data collection process for each selected annotation.
 * The process includes tiling, running a Python command for data collection, and optionally stitching the collected data.
 * Post-collection, it handles the tiles according to user preference, including deletion or compression.
 */
    static void secondModalityGUI() {
        //TODO check if in a project?
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        // Create the dialog
        def dlg = new Dialog<ButtonType>()
        dlg.initModality(Modality.APPLICATION_MODAL)
        dlg.setOnShown(event -> {
            Window window = dlg.getDialogPane().getScene().getWindow();
            if (window instanceof Stage) {
                ((Stage) window).setAlwaysOnTop(true);
            }
        });
        dlg.setTitle("Collect image data from an annotated subset of your current image.")
        dlg.setHeaderText("Create annotations within your image, then click Okay to proceed with a second collection within those areas.")

        // Set the content
        dlg.getDialogPane().setContent(createSecondModalityGUI())

        // Add Okay and Cancel buttons
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL)

        // Show the dialog and capture the response
        def result = dlg.showAndWait()

        // Handling the response
        logger.info("Starting processing GUI output")
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Retrieve values from text fields
            def sampleLabel = sampleLabelField.getText()
            def classFilter = classFilterField.getText().split(',').collect { it.trim() }


            //Preferences from GUI
            double frameWidth = preferences.find{it.getName() == "Camera Frame Width #px"}.getValue() as Double
            double frameHeight = preferences.find{it.getName() == "Camera Frame Height #px"}.getValue() as Double
            double pixelSizeFirstScanType = preferences.find{it.getName() == "1st scan pixel size um"}.getValue() as Double
            double pixelSizeSecondScanType = preferences.find{it.getName() == "2nd scan pixel size um"}.getValue() as Double
            double overlapPercent = preferences.find{it.getName() == "Tile Overlap Percent"}.getValue() as Double
            String projectsFolderPath = preferences.find{it.getName() == "Projects Folder"}.getValue() as String
            String virtualEnvPath =  preferences.find{it.getName() == "Python Environment"}.getValue() as String
            String pythonScriptPath =  preferences.find{it.getName() == "PycroManager Path"}.getValue() as String
            String compressionType = preferences.find{it.getName() == "Compression type"}.getValue() as String
            String tileHandling = preferences.find{it.getName() == "Tile Handling Method"}.getValue() as String
            String secondScanType = preferences.find{it.getName() == "Second Scan Type"}.getValue() as String

            //SETUP: collect variables
            QuPathGUI qupathGUI = QPEx.getQuPath()
            String scanTypeWithIndex = MinorFunctions.getUniqueFolderName(projectsFolderPath + File.separator + sampleLabel + File.separator + secondScanType)
            String tempTileDirectory = projectsFolderPath + File.separator + sampleLabel + File.separator + scanTypeWithIndex
            Project<BufferedImage> currentQuPathProject = QP.getProject()
            //Boolean to check whether to proceed with running the microscope data collection
            logger.info("getting annotation objects")
            def annotations = QP.getAnnotationObjects()
            annotations = annotations.findAll{classFilter.contains(it.getPathClass().toString())}

            // Check if we have sufficient information to proceed
            if (annotations.isEmpty() || [sampleLabel, virtualEnvPath, pythonScriptPath].any { it == null || it.isEmpty() }) {
                Dialogs.showWarningNotification("Warning!", "Insufficient data to send command to microscope!")
                return
            }
            //Convert the camera frame width/height into pixels in the image we are working on.
            Double frameWidthQPpixels = (frameWidth ) / (pixelSizeFirstScanType ) * (pixelSizeSecondScanType )
            Double frameHeightQPpixels = (frameHeight ) / (pixelSizeFirstScanType) * (pixelSizeSecondScanType )
            UtilityFunctions.performTilingAndSaveConfiguration(tempTileDirectory,
                    scanTypeWithIndex,
                    frameWidthQPpixels,
                    frameHeightQPpixels,
                    overlapPercent,
                    null,
                    true,
                    annotations)

            logger.info("Scan type with index: " + scanTypeWithIndex)
            logger.info(tempTileDirectory)

// A semaphore to control the stitching process
            Semaphore stitchingSemaphore = new Semaphore(1)


            for (annotation in annotations) {
                String annotationName = annotation.getName()
                List args = [projectsFolderPath,
                             sampleLabel,
                             scanTypeWithIndex,
                             annotationName]


                //Progress bar that updates by checking target folder for new images?
                UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, args)
                //TODO how can we distinguish between a hung python run and one that is taking a long time? - possibly check for new files in target folder?
                //TODO Need to check if stitching is successful, provide error
                //while projectsFolderPath/sampleLabel/scanTypeWithIndex/annotationName does not have
                //fileCount = number of entries in TileConfiguration.txt OR getDetectionObjects().size()+1
                //Loop checking each second for file count files within the folder
                //Track loops, after N loops, break and end program with error.

                logger.info("Finished Python Command for $annotationName")
                // Start a new thread for stitching
                Thread.start {
                    try {
                        // Acquire a permit before starting stitching
                        stitchingSemaphore.acquire()

                        logger.info("Begin stitching")
                        String stitchedImagePathStr = UtilityFunctions.stitchImagesAndUpdateProject(projectsFolderPath,
                                sampleLabel, scanTypeWithIndex as String, annotationName,
                                qupathGUI, currentQuPathProject, compressionType)
                        logger.info(stitchedImagePathStr)


                    } catch (InterruptedException e) {
                        logger.error("Stitching thread interrupted", e)
                    } finally {
                        // Release the semaphore for the next stitching process
                        stitchingSemaphore.release()
                    }
                }

            }
            // Post-stitching tasks like deleting or zipping tiles
            //Check if the tiles should be deleted from the collection folder
            if (tileHandling == "Delete")
                UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
            if (tileHandling == "Zip") {
                UtilityFunctions.zipTilesAndMove(tempTileDirectory)
                UtilityFunctions.deleteTilesAndFolder(tempTileDirectory)
            }
        }
    }



/**
 * Creates and configures a dialog for inputting macro image settings, including sample label and tissue detection script paths.
 * The dialog is designed to collect configuration settings for macro view imaging, ensuring that it stays on top of other windows
 * for better visibility and interaction. It initializes with application modal modality and sets the title and header text
 * to guide the user through the configuration process. The content of the dialog is populated with a dynamically created GUI
 * for input fields related to macro image settings.
 *
 * @return The configured Dialog instance ready for showing to the user.
 */

    private static Dialog<ButtonType> createMacroImageInputDialog() {
        def dlg = new Dialog<ButtonType>()
        dlg.initModality(Modality.APPLICATION_MODAL)
        dlg.setOnShown(event -> {
            Window window = dlg.getDialogPane().getScene().getWindow();
            if (window instanceof Stage) {
                ((Stage) window).setAlwaysOnTop(true);
            }
        });
        dlg.setTitle("Macro View Configuration")
        dlg.setHeaderText("Configure settings for macro view.")
        dlg.getDialogPane().setContent(createMacroImageInputGUI())
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL)
        // Accessing the Window of the Dialog to set always on top
        dlg.setOnShown(event -> {
            Window window = dlg.getDialogPane().getScene().getWindow();
            if (window instanceof Stage) {
                ((Stage) window).setAlwaysOnTop(true);
            }
        });
        return dlg
    }

/**
 * Constructs a GridPane layout containing input fields and labels for configuring macro image settings.
 * This method dynamically adds UI components to a GridPane for collecting settings such as the sample label,
 * the path to a tissue detection script, and pixel size. It is designed to be used as the content of a dialog
 * or another container within a GUI. Each row of the grid contains a different setting, organized for clarity and ease of use.
 *
 * @return A GridPane containing the configured UI components for macro image input settings.
 */
    private static GridPane createMacroImageInputGUI() {
        GridPane pane = new GridPane()
        pane.setHgap(10)
        pane.setVgap(10)
        def row = 0

        // Add new components for the checkbox and Groovy script path
        UI_functions.addToGrid(pane, new Label('Sample Label:'), sampleLabelField, row++)
        // Add components for Python environment and script path

        UI_functions.addToGrid(pane, new Label('Tissue detection script:'), groovyScriptField, row++)
        // Add new components for pixel size and non-isotropic pixels checkbox on the same line
        HBox pixelSizeBox = new HBox(10)
        pixelSizeBox.getChildren().addAll(new Label('Pixel Size XY um:'), pixelSizeField, nonIsotropicCheckBox)
        UI_functions.addToGrid(pane, pixelSizeBox, row++)


        return pane
    }


    /**
     * Displays a dialog to the user for tile selection to match the live view in the microscope with QuPath's coordinate system.
     * This function ensures that exactly one tile (detection) is selected by the user. If the selection does not meet
     * the criteria (exactly one detection), the user is prompted until a valid selection is made or the operation is cancelled.
     *
     * The dialog is set to always be on top to ensure visibility to the user. If the user cancels or closes the dialog
     * without making a valid selection, the function returns false to indicate the operation was not completed.
     *
     * @return true if a valid tile is selected and the user confirms the selection; false if the user cancels the operation.
     */
    static boolean stageToQuPathAlignmentGUI1() {
        boolean validTile = false;
        Optional<ButtonType> result;

        while (!validTile) {
            // Create and configure the dialog inside the loop
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.initModality(Modality.NONE);
            dlg.setTitle("Identify Location");
            dlg.setHeaderText("Select one tile (a detection) and match the Live View in uManager to the location of that tile, as closely as possible.\n This will be used for matching QuPath's coordinate system to the microscope stage coordinate system, so be as careful as you can!");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.setOnShown(event -> {
                Window window = dlg.getDialogPane().getScene().getWindow();
                if (window instanceof Stage) {
                    ((Stage) window).setAlwaysOnTop(true);
                }
            });

            // Show the dialog and wait for the user response
            result = dlg.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                List selectedObjects = QP.getSelectedObjects().stream()
                        .filter(object -> object.isDetection() && object.getROI() instanceof qupath.lib.roi.RectangleROI)
                        .collect(Collectors.toList());

                if (selectedObjects.size() != 1) {
                    MinorFunctions.showAlertDialog("There needs to be exactly one tile selected.");
                } else {
                    validTile = true;
                }
            } else {
                // User cancelled or closed the dialog
                return false;
            }
        }
        return true;
    }


/**
 * Displays a dialog to confirm the accuracy of the current stage position in comparison with the live view.
 * This dialog is part of the process to calibrate the "Live view" stage position with the position of a single field of view in the preview image.
 * The user is presented with two options: to confirm the current position is accurate or to cancel the acquisition.
 *
 * @return {@code true} if the user confirms the current position is accurate, {@code false} if the user cancels the acquisition.
 */
    static boolean stageToQuPathAlignmentGUI2() {
        // Create a custom dialog with application modal modality.
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL); // Prevents interaction with other windows until this dialog is closed.
        dlg.setTitle("Position Confirmation");
        // Header text explaining the purpose of the dialog and providing instructions for comparison.
        dlg.setHeaderText("Is the current position accurate? Compare with the uManager live view!\n" +
                "The first time this dialog shows up, it should select the center of the top row!\n" +
                "The second time, it should select the center of the left-most column!");
        dlg.setOnShown(event -> {
            Window window = dlg.getDialogPane().getScene().getWindow();
            if (window instanceof Stage) {
                ((Stage) window).setAlwaysOnTop(true);
            }
        });
        // Define button types for user actions.
        ButtonType btnCurrentPositionAccurate = new ButtonType("Current Position is Accurate", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelAcquisition = new ButtonType("Cancel acquisition", ButtonBar.ButtonData.CANCEL_CLOSE);

        // Add buttons to the dialog pane.
        dlg.getDialogPane().getButtonTypes().addAll(btnCurrentPositionAccurate, btnCancelAcquisition);

        // Process the user's button click to determine the return value.
        dlg.setResultConverter(dialogButton -> {
            if (dialogButton == btnCurrentPositionAccurate) {
                // If the user confirms the current position is accurate, return true.
                return true;
            } else if (dialogButton == btnCancelAcquisition) {
                // If the user cancels the acquisition, return false.
                return false;
            }
            return null; // Return null if another close mechanism is triggered.
        });

        // Show the dialog and wait for the user to make a selection, then return the result.
        Optional<Boolean> result = dlg.showAndWait();

        // Return the user's decision or false if no explicit decision was made.
        return result.orElse(false);
    }


//    static stageToQuPathAlignmentGUI2() {
//        List<String> choices = Arrays.asList("Current Position is Accurate", "Cancel acquisition")
//        ChoiceDialog<String> dialog = new ChoiceDialog<>("Current Position is Accurate", choices)
//        dialog.initModality(Modality.NONE)
//        dialog.setTitle("Position Confirmation")
//        dialog.setHeaderText("Is the current position accurate? Compare with the uManager live view!\n The first time this dialog shows up, it should select the center of the top row! \n The second time, it should select the center of the left-most column!")
//
//        Optional<String> result = dialog.showAndWait()
//        if (result.isPresent()) {
//
//            return result.get()
//
//        }
//
//        // If no choice is made (e.g., dialog is closed), you can decide to return false or handle it differently
//        return false
//    }

    /**
     * Handles the process of selecting a tile, transforming its coordinates, moving the stage,
     * validating the new stage position, and updating the transformation.
     *
     * @param tileXY The tile coordinates and object.
     * @param qupathGUI The QuPath GUI instance.
     * @param virtualEnvPath
     The virtual environment path for Python commands.

     @param pythonScriptPath The Python script path.

     @param transformation The current AffineTransform.

     @return A boolean indicating if the position was validated successfully and the updated transformation.
     */

    private static Map<String, Object> handleStageAlignment(PathObject tileXY, QuPathGUI qupathGUI,
                                                            String virtualEnvPath, String pythonScriptPath,
                                                            AffineTransform transformation) {
        QP.selectObjects(tileXY)
        // Transform the QuPath coordinates into stage coordinates
        def QPPixelCoordinates = [tileXY.getROI().getCentroidX(), tileXY.getROI().getCentroidY()]
        List expectedStageXYPositionMicrons = TransformationFunctions.QPtoMicroscopeCoordinates(QPPixelCoordinates, transformation)
        logger.info("QuPath pixel coordinates: $QPPixelCoordinates")
        logger.info("Transformed into stage coordinates: $expectedStageXYPositionMicrons")
        // Move the stage to the new coordinates
        UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, expectedStageXYPositionMicrons as List<String>)
        qupathGUI.getViewer().setCenterPixelLocation(tileXY.getROI().getCentroidX(), tileXY.getROI().getCentroidY())

        // Validate the position that was moved to or update with an adjusted position
        def updatePosition = stageToQuPathAlignmentGUI2()
        if (updatePosition.equals("Use adjusted position")) {
            // Get access to current stage coordinates and update transformation
            List currentStageCoordinates_um = UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, null)

            transformation = TransformationFunctions.addTranslationToScaledAffine(transformation, QPPixelCoordinates as List<Double>, currentStageCoordinates_um as List<Double>)
        }

        // Prepare the results to be returned
        Map<String, Object> results = [
                'updatePosition': updatePosition,
                'transformation': transformation
        ]

        return results
    }

    //Create the second interface window for performing higher resolution or alternate modality scans
    private static GridPane createSecondModalityGUI() {
        GridPane pane = new GridPane()
        pane.setHgap(10)
        pane.setVgap(10)
        def row = 0

        // Add new component for Sample Label
        UI_functions.addToGrid(pane, new Label('Sample Label:'), sampleLabelField, row++)
        UI_functions.addToGrid(pane, new Label('Annotation classes to image:'), classFilterField, row++)

        // Listener for the checkbox

        return pane
    }


    static CompletableFuture<List<String>> runPythonCommandAsync(String virtualEnvPath, String pythonScriptPath, List<String> args, Semaphore pythonCommandSemaphore) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                pythonCommandSemaphore.acquire(); // Ensure only one command runs at a time
                return UtilityFunctions.runPythonCommand(virtualEnvPath, pythonScriptPath, args);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null; // Handle this appropriately
            } finally {
                pythonCommandSemaphore.release();
            }
        });
    }


}
