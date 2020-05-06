package uob.flexiweld.app;

import com.sun.istack.internal.Nullable;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import uob.flexiweld.Utils;
import uob.flexiweld.geom.Line;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlignmentMode extends CheckerboardDetectionMode {

	public static final Scalar GRID_COLOUR = Utils.MAGENTA;

	@Nullable
	private final CalibrationSettings calibrationSettings;
	@Nullable
	private Mat alignmentMatrix; // N.B. This isn't final here

	private final Line[] grid;
	private final List<Line> transformedGrid = new ArrayList<>();

	private JButton alignButton;
	private JButton doneButton;

	public AlignmentMode(Size checkerboardSize, double squareSize, @Nullable CalibrationSettings calibrationSettings, @Nullable Mat alignmentMatrix){
		super("Alignment", checkerboardSize, squareSize);
		this.calibrationSettings = calibrationSettings;
		this.alignmentMatrix = alignmentMatrix;
		grid = Utils.generateGrid((int)checkerboardSize.width + 1, (int)checkerboardSize.height + 1,
				new Size((checkerboardSize.width + 1) * squareSize, (checkerboardSize.height + 1) * squareSize),
				new Size(-squareSize, -squareSize));
		transformGrid();
	}

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

	private void performAlignment(){
		// Because we're using the raw checkerboard points this time (rather than rvecs and tvecs) we can calculate the
		// transform directly without having to reproject four arbitrary points (like we did in Calibrator)
		// All we need to do is select four corners (the further apart the better) and their corresponding object points
		List<Point> corners = getCorners().toList();

		List<Point> imagePts = new ArrayList<>();
		imagePts.add(corners.get(0)); // Top left corner
		imagePts.add(corners.get((int)checkerboardSize.width - 1)); // Top right corner
		imagePts.add(corners.get(corners.size() - (int)checkerboardSize.width)); // Bottom left corner
		imagePts.add(corners.get(corners.size() - 1)); // Bottom right corner

		List<Point> worldPts = new ArrayList<>();
		worldPts.add(new Point(0, 0)); // Top left corner
		worldPts.add(new Point((checkerboardSize.width - 1) * squareSize, 0)); // Top right corner
		worldPts.add(new Point(0, (checkerboardSize.height - 1) * squareSize)); // Bottom left corner
		worldPts.add(new Point((checkerboardSize.width - 1) * squareSize, (checkerboardSize.height - 1) * squareSize)); // Bottom right corner

		Mat src = Converters.vector_Point2f_to_Mat(worldPts);
		Mat dst = Converters.vector_Point2f_to_Mat(imagePts);

		alignmentMatrix = Imgproc.getPerspectiveTransform(src, dst);

		transformGrid();
	}

	private void transformGrid(){

		if(alignmentMatrix == null) return;

		transformedGrid.clear();

		for(Line line : grid){
			List<Point> points = Arrays.asList(line.getStart(), line.getEnd());
			Mat lmat = Converters.vector_Point2f_to_Mat(points);
			lmat = Utils.process(lmat, (s, d) -> Core.perspectiveTransform(s, d, alignmentMatrix));
			points = new ArrayList<>();
			Converters.Mat_to_vector_Point2f(lmat, points);
			transformedGrid.add(new Line(points.get(0), points.get(1)).extended(10));
		}
	}

	private void exitAlignment(FlexiweldApp app){
		app.setMode(new MeasurementMode(calibrationSettings, alignmentMatrix));
	}

}
