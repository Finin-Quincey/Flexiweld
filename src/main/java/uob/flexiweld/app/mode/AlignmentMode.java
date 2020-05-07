package uob.flexiweld.app.mode;

import com.sun.istack.internal.Nullable;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;
import uob.flexiweld.geom.Line;
import uob.flexiweld.util.CalibrationSettings;
import uob.flexiweld.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Capture mode responsible for performing alignment of the camera with the measurement plane. This class handles the
 * calculation of the transform matrix describing the position and orientation of the measurement plane.
 * @author Finin Quincey
 */
public class AlignmentMode extends CheckerboardDetectionMode {

	/** The display colour of the grid indicating the current alignment. */
	private static final Scalar GRID_COLOUR = Utils.toScalar(new Color(0x48FFA4));

	/** The current calibration settings used for undistorting the video feed. */
	@Nullable private final CalibrationSettings calibrationSettings;
	/** The current alignment matrix used to calculate the real-world positions of image points. */
	@Nullable private Mat alignmentMatrix; // N.B. This isn't final in this class

	/** A list of lines representing the un-transformed grid, so it only needs to be generated once. */
	private final List<Line> grid;
	/** A grid of lines to display on the screen to indicate the current alignment. */
	private List<Line> transformedGrid;

	// UI components
	private JButton alignButton;
	private JButton doneButton;

	public AlignmentMode(Size checkerboardSize, double squareSize, @Nullable CalibrationSettings calibrationSettings, @Nullable Mat alignmentMatrix){
		super("Alignment", checkerboardSize, squareSize);
		this.calibrationSettings = calibrationSettings;
		this.alignmentMatrix = alignmentMatrix;
		grid = Arrays.asList(Utils.generateGrid((int)checkerboardSize.width + 1, (int)checkerboardSize.height + 1,
				new Size((checkerboardSize.width + 1) * squareSize, (checkerboardSize.height + 1) * squareSize),
				new Size(-squareSize, -squareSize)));
		transformGrid();
	}

	/** Returns true if the camera is calibrated, false if not. */
	public boolean isCalibrated(){
		return calibrationSettings != null;
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){
		super.populateControls(app, components);
		components.add(alignButton = FlexiweldApp.createButton("\u25a6 Set", e -> performAlignment()));
		components.add(doneButton = FlexiweldApp.createButton(FlexiweldApp.CHECK_MARK + "Done", e -> exitAlignment(app)));
		doneButton.setForeground(FlexiweldApp.CONFIRM_TEXT_COLOUR);
	}

	@Override
	public void populateStatusBar(List<Component> components){

		super.populateStatusBar(components);

		if(isCalibrated()){
			FlexiweldApp.addConfirmText("Calibrated", components);
		}else{
			FlexiweldApp.addWarningText("Not calibrated", components);
		}

	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){

		// This MUST be done BEFORE calling super! Otherwise alignment will be done w.r.t. the *uncalibrated* image!
		if(isCalibrated()){
			frame = calibrationSettings.undistort(frame); // Lens correction
		}

		frame = super.processFrame(videoFeed, frame);

		// Interesting test, keeps the grid in the same place and warps the image instead
//		if(alignmentMatrix != null){
//			double width = squareSize * checkerboardSize.width;
//			double height = squareSize * checkerboardSize.height;
//			raw = Utils.process(raw, (s, d) -> Imgproc.warpPerspective(s, d, alignmentMatrix.inv(), videoFeed.getCameraResolution()));
//			raw = new Mat(raw, new Rect(0, 0, (int)width, (int)height));
//		}

		return frame;
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){

		for(Line line : transformedGrid){
			Imgproc.line(frame, videoFeed.transformForDisplay(line.getStart()),
					videoFeed.transformForDisplay(line.getEnd()), GRID_COLOUR, 2);
		}

		alignButton.setEnabled(foundCheckerboard());

		return super.annotateFrame(videoFeed, frame);
	}

	/**
	 * Recalculates the alignment matrix required to transform the image coordinates of the currently-detected
	 * checkerboard points to their (predefined) real-world positions, and updates the displayed grid accordingly.
	 */
	private void performAlignment(){
		// Because we're using the raw checkerboard points this time (rather than rvecs and tvecs) we can calculate the
		// transform directly without having to reproject four arbitrary points (like we did in Calibrator)
		// All we need to do is select four corners (the further apart the better) and their corresponding object points
		List<Point> corners = getCorners().toList();

		// Retrieve the outermost corners from the image points and add them to a list
		List<Point> imagePts = new ArrayList<>();
		imagePts.add(corners.get(0)); // Top left corner
		imagePts.add(corners.get((int)checkerboardSize.width - 1)); // Top right corner
		imagePts.add(corners.get(corners.size() - (int)checkerboardSize.width)); // Bottom left corner
		imagePts.add(corners.get(corners.size() - 1)); // Bottom right corner

		// Construct the corresponding world points and add them to a list
		List<Point> worldPts = new ArrayList<>();
		worldPts.add(new Point(0, 0)); // Top left corner
		worldPts.add(new Point((checkerboardSize.width - 1) * squareSize, 0)); // Top right corner
		worldPts.add(new Point(0, (checkerboardSize.height - 1) * squareSize)); // Bottom left corner
		worldPts.add(new Point((checkerboardSize.width - 1) * squareSize, (checkerboardSize.height - 1) * squareSize)); // Bottom right corner

		// Pack both sets of points into matrices ready to be processed
		// Remember, the alignment matrix transforms *image* points to *world* points
		Mat src = Converters.vector_Point2f_to_Mat(imagePts);
		Mat dst = Converters.vector_Point2f_to_Mat(worldPts);

		// Calculate the transform matrix required to transform src to dst
		alignmentMatrix = Imgproc.getPerspectiveTransform(src, dst);

		transformGrid(); // Update the displayed grid lines
	}

	/** Updates the displayed grid lines based on the current alignment matrix. */
	private void transformGrid(){

		if(alignmentMatrix == null) return;

		MatOfPoint2f lmat = new MatOfPoint2f();
		// Invert the alignment matrix because here, we're transforming the other way (world -> image)
		Core.perspectiveTransform(Line.pack(grid), lmat, alignmentMatrix.inv()); // Pack into matrix and transform
		transformedGrid = Line.unpack(lmat); // Unpack back to list of lines
	}

	/** Exits alignment mode and returns to measurement mode with the current alignment matrix. */
	private void exitAlignment(FlexiweldApp app){
		app.setMode(new MeasurementMode(calibrationSettings, alignmentMatrix));
	}

}
