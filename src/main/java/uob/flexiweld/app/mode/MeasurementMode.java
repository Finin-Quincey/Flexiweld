package uob.flexiweld.app.mode;

import com.sun.istack.internal.Nullable;
import org.opencv.core.Point;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;
import uob.flexiweld.geom.Intersection;
import uob.flexiweld.geom.Line;
import uob.flexiweld.geom.LineTracker;
import uob.flexiweld.util.CalibrationSettings;
import uob.flexiweld.util.Utils;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Capture mode responsible for processing and display during normal operation, when objects are being measured. This
 * class performs line tracking, intersection finding, measurement calculation and annotation of the detected features.
 * @author Finin Quincey
 */
public class MeasurementMode extends LiveMode {

	// Constants

	// Defining these using java colours because it lets me use the colour picker in my IDE :D
	/** The display colour of the averaged lines. */
	private static final Scalar LINE_COLOUR = 			Utils.toScalar(new Color(0x4785FF));
	/** The display colour of the centrelines. */
	private static final Scalar CENTRELINE_COLOUR = 	Utils.toScalar(new Color(0x00FFFF));
	/** The display colour of the measured line segments and their lengths. */
	private static final Scalar SEGMENT_COLOUR = 		Utils.toScalar(new Color(0x7FFF43));
	/** The display colour of the intersection points. */
	private static final Scalar INTERSECTION_COLOUR = 	Utils.toScalar(new Color(0xFFB100));
	/** The display colour of the measured angles between segments. */
	private static final Scalar ANGLE_COLOUR = 			Utils.toScalar(new Color(0xffff00));

	/** The distance from the centre of an angle measurement to the value readout, in pixels. */
	private static final double ANGLE_DISPLAY_RADIUS = 60;
	/** The width and height of the arc displayed to denote a measured angle, in pixels (size is for the full circle). */
	private static final Size ELLIPSE_SIZE = new Size(ANGLE_DISPLAY_RADIUS - 10, ANGLE_DISPLAY_RADIUS - 10);

	// TODO: Make these configurable
	/** Lines within this angle of each other are considered parallel */
	private static final double ANGLE_THRESHOLD = Math.toRadians(5);
	/** Pairs of parallel, non-coincident lines within this distance of each other are considered to be tubes */
	private static final double WIDTH_THRESHOLD = 50; // Excludes e.g. the edges of the test card

	private static final Mat IDENTITY_MATRIX_3X3 = Mat.eye(3, 3, CvType.CV_32F);

	// Processing parameters

	/** The current calibration settings used for undistorting the video feed. */
	@Nullable private final CalibrationSettings calibrationSettings;
	/** The current alignment matrix used to calculate the real-world positions of image points. */
	@Nullable private final Mat alignmentMatrix;

	/** The {@link LineTracker} object used to track the positions of lines over multiple frames. */
	private final LineTracker lineTracker;

	// Detected features

	/** Stores the list of averaged lines output by the line tracker each frame. */
	private List<Line> averagedLines;
	/** Stores the list of centrelines found from the averaged lines each frame.  */
	private List<Line> centrelines;

	/** Maps intersections to angles of intersection. */
	private Map<Intersection, Double> intersections;
	/** Maps line segments (in image space) to their lengths (in world space). */
	private Map<Line, Double> segments;

	// Display settings (these are pretty self-explanatory)

	private boolean showLines = false;
	private boolean showCentrelines = false;
	private boolean showSegments = true;
	private boolean showIntersections = false;
	private boolean showAngles = true;

	/** Creates a new {@code MeasurementMode} with null calibration settings and alignment matrix. */
	public MeasurementMode(){
		this(null, null);
	}

	/** Creates a new {@code MeasurementMode} with the given calibration settings and alignment matrix. */
	public MeasurementMode(@Nullable CalibrationSettings calibrationSettings, @Nullable Mat alignmentMatrix){
		super("Measuring");
		this.calibrationSettings = calibrationSettings;
		this.alignmentMatrix = alignmentMatrix;
		this.lineTracker = new LineTracker(5);
		this.intersections = new HashMap<>();
		this.segments = new HashMap<>();
	}

	/** Returns true if the camera is calibrated, false if not. */
	public boolean isCalibrated(){
		return calibrationSettings != null;
	}

	/** Returns true if the camera is aligned, false if not. */
	public boolean isAligned(){
		return alignmentMatrix != null;
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){

		super.populateControls(app, components);

		// Calibration and alignment buttons
		components.add(FlexiweldApp.createButton("\u25a9 " + (isCalibrated() ? "Recalibrate" : "Calibrate"), e -> prepareCalibration(app)));
		components.add(FlexiweldApp.createButton("\u25a3 " + (isAligned() ? "Realign" : "Align"), e -> prepareAlignment(app)));

		// Annotation visibility toggle buttons
		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Lines", 		Utils.toColor(LINE_COLOUR), 		showLines, 			e -> showLines 			= !showLines));
		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Centrelines", 	Utils.toColor(CENTRELINE_COLOUR), 	showCentrelines, 	e -> showCentrelines 	= !showCentrelines));
		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Segments", 		Utils.toColor(SEGMENT_COLOUR), 		showSegments, 		e -> showSegments 		= !showSegments));
		components.add(FlexiweldApp.createFancyToggleButton("\u2795 Corners", 		Utils.toColor(INTERSECTION_COLOUR), showIntersections, 	e -> showIntersections 	= !showIntersections));
		components.add(FlexiweldApp.createFancyToggleButton("\u2aa6 Angles", 		Utils.toColor(ANGLE_COLOUR), 		showAngles, 		e -> showAngles 		= !showAngles));
	}

	@Override
	public void populateStatusBar(List<Component> components){

		super.populateStatusBar(components);

		if(isCalibrated()){
			FlexiweldApp.addConfirmText("Calibrated", components);
		}else{
			FlexiweldApp.addWarningText("Not calibrated", components);
		}

		if(isAligned()){
			FlexiweldApp.addConfirmText("Aligned", components);
		}else{
			FlexiweldApp.addWarningText("Not aligned", components);
		}
	}

	@Override
	public Mat processFrame(VideoFeed videoFeed, Mat frame){

		if(isCalibrated()){
			frame = calibrationSettings.undistort(frame); // Apply lens correction first
		}

		// "Image space" refers to coordinates in the undistorted camera frame in pixels, with no other processing
		// "World space" refers to actual coordinates in the world, in millimetres (assuming objects are in-plane)
		// "Screen space" refers to coordinates on the screen in pixels, as displayed

		// alignmentMatrix transforms *image* space to *world* space
		// VideoFeed#transformForDisplay transforms *image* space to *screen* space

		// Discard the intersections and lines from the previous frame
		intersections.clear();
		segments.clear();

		// ============================================================================================================
		// Perform geometry processing in the (undistorted) image space because it's quicker since it has fewer pixels,
		// and because we won't gain any accuracy by scaling first - accuracy is still limited by the camera resolution
		// ============================================================================================================

		// Get the averaged lines for this frame from the line tracker
		averagedLines = lineTracker.processNextFrame(frame);
		// Find the centrelines from those and store them
		centrelines = Utils.findCentrelines(averagedLines, WIDTH_THRESHOLD, ANGLE_THRESHOLD);
		// Sort by angle for easier processing later
		centrelines.sort(Comparator.comparing(Line::angle).reversed());

		// Init intersection lists, to be populated by Intersection.intersect(...)
		List<Intersection> intersectionList = new ArrayList<>();
		List<Line> segmentList = new ArrayList<>();

		// Find segments and intersections
		Intersection.intersect(centrelines, intersectionList, segmentList);

		// ============================================================================================================
		// Transform results into the world space to get the actual measurements, and store them in maps for later
		// ============================================================================================================

		MatOfPoint2f dst = new MatOfPoint2f(); // Initialise destination matrix for perspective transform

		Mat transform = isAligned() ? alignmentMatrix : IDENTITY_MATRIX_3X3; // Identity matrix keeps points the same

		// Intersections
		if(!intersectionList.isEmpty()){

			// Pack the intersections into a matrix and perform the perspective transform on them
			Core.perspectiveTransform(Intersection.pack(intersectionList), dst, transform);
			// Unpack the resulting matrix back into a list of intersections
			List<Intersection> transformedInts = Intersection.unpack(dst);

			for(int i = 0; i < intersectionList.size(); i++){
				// Measure angle between lines in *world* space
				double angle = Line.acuteAngleBetween(transformedInts.get(i).getLineA(), transformedInts.get(i).getLineB());
				// Map *image* space intersection to the angle we just calculated, for annotation use later
				intersections.put(intersectionList.get(i), angle);
			}
		}

		// Segments
		if(!segmentList.isEmpty()){

			// Technically we're transforming some points again here, but that's a fairly inexpensive operation compared
			// to the alternative of performing the intersections twice (unfortunately this is the price we have to pay
			// for modularity/maintainability, i.e. not having it all in one loop like it is in LineDetectorTest)
			Core.perspectiveTransform(Line.pack(segmentList), dst, transform); // Pack into matrix and transform
			List<Line> transformedLines = Line.unpack(dst); // Unpack back to list of lines

			for(int i = 0; i < segmentList.size(); i++){
				// Map *image* space line to the length of the corresponding line in *world* space
				segments.put(segmentList.get(i), transformedLines.get(i).length());
			}
		}

		return frame; // Return the undistorted frame for further processing
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){

		if(showLines){
			for(Line line : averagedLines){
				Imgproc.line(frame, videoFeed.transformForDisplay(line.getStart()), videoFeed.transformForDisplay(line.getEnd()),
						LINE_COLOUR, 2);
			}
		}

		if(showCentrelines){
			for(Line line : centrelines){
				Imgproc.line(frame, videoFeed.transformForDisplay(line.getStart()), videoFeed.transformForDisplay(line.getEnd()),
						CENTRELINE_COLOUR, 2);
			}
		}

		if(showSegments){
			for(Line segment : segments.keySet()){
				Line line = videoFeed.transformForDisplay(segment);
				Imgproc.line(frame, line.getStart(), line.getEnd(), SEGMENT_COLOUR, 2);
				Imgproc.putText(frame, String.format("%.2fmm", segments.get(segment)), line.midpoint(),
						Core.FONT_HERSHEY_PLAIN, 2, SEGMENT_COLOUR, 2);
			}
		}

		for(Intersection intersection : intersections.keySet()){

			Point point = videoFeed.transformForDisplay(intersection.getPoint());

			if(showIntersections){
				Imgproc.drawMarker(frame, point, INTERSECTION_COLOUR, Imgproc.MARKER_CROSS, 14, 2);
				// TODO: If we're going to display coordinates, we need to store the original point
//				Imgproc.putText(frame, String.format("(%.2f, %.2f)", point.x, point.y), point,
//						Core.FONT_HERSHEY_PLAIN, 2, INTERSECTION_COLOUR, 2);
			}

			if(showAngles){

				// Determine absolute start and end angle for the arc to be displayed
				// Because the centrelines were sorted by angle earlier, line B always has the smaller angle
				double startAngle = videoFeed.transformForDisplay(intersection.getLineB()).angle();
				double endAngle = videoFeed.transformForDisplay(intersection.getLineA()).angle();

				if(startAngle < -Math.PI / 2) startAngle += Math.PI;

				if(endAngle - startAngle > Math.PI / 2){
					double prevStartAngle = startAngle;
					startAngle = endAngle - Math.PI;
					endAngle = prevStartAngle;
				}

				double midAngle = (startAngle + endAngle) / 2; // Should be fine since centrelines are rectified

				Imgproc.ellipse(frame, point, ELLIPSE_SIZE, 0, Math.toDegrees(startAngle),
						Math.toDegrees(endAngle), ANGLE_COLOUR, 2);

				Imgproc.putText(frame, String.format("%.2fdeg", Math.toDegrees(intersections.get(intersection))),
						new Point(point.x + ANGLE_DISPLAY_RADIUS * Math.cos(midAngle),
								point.y + ANGLE_DISPLAY_RADIUS * Math.sin(midAngle) + 10),
						Core.FONT_HERSHEY_PLAIN, 2, ANGLE_COLOUR, 2);
			}
		}

		return super.annotateFrame(videoFeed, frame);
	}

	/** Prompts the user to enter calibration parameters and then switches the app into calibration mode. */
	private void prepareCalibration(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the calibration parameters below
		app.setMode(new CalibrationMode(new Size(9, 6), 25.5, calibrationSettings, alignmentMatrix));
	}

	/** Prompts the user to enter alignment parameters and then switches the app into alignment mode. */
	private void prepareAlignment(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the alignment parameters below
		app.setMode(new AlignmentMode(new Size(9, 6), 25.5, calibrationSettings, alignmentMatrix));
	}

}
