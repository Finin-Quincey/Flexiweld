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

public class MeasurementMode extends LiveMode {

	private static final Scalar LINE_COLOUR = Utils.BLUE;
	private static final Scalar CENTRELINE_COLOUR = Utils.CYAN;
	private static final Scalar SEGMENT_COLOUR = Utils.WHITE;
	private static final Scalar INTERSECTION_COLOUR = Utils.YELLOW;
	private static final Scalar ANGLE_COLOUR = Utils.GREEN;

	private static final double ANGLE_DISPLAY_RADIUS = 60;
	private static final Size ELLIPSE_SIZE = new Size(ANGLE_DISPLAY_RADIUS - 10, ANGLE_DISPLAY_RADIUS - 10);

	// TODO: Make these configurable
	/** Lines within this angle of each other are considered parallel */
	public static final double ANGLE_THRESHOLD = Math.toRadians(10);
	/** Pairs of parallel, non-coincident lines within this distance of each other are considered to be tubes */
	public static final double WIDTH_THRESHOLD = 200; // Excludes e.g. the edges of the test card

	@Nullable
	private final CalibrationSettings calibrationSettings;
	@Nullable
	private final Mat alignmentMatrix;

	private final LineTracker lineTracker;

	private List<Line> averagedLines;
	private List<Line> centrelines;

	/** Maps intersections to angles of intersection. */
	private Map<Intersection, Double> intersections;
	/** Maps line segments (in image space) to their lengths (in world space). */
	private Map<Line, Double> segments;

	private boolean showLines = false;
	private boolean showCentrelines = false;
	private boolean showSegments = true;
	private boolean showIntersections = false;
	private boolean showAngles = true;

	public MeasurementMode(){
		this(null, null);
	}

	public MeasurementMode(@Nullable CalibrationSettings calibrationSettings, @Nullable Mat alignmentMatrix){
		super("Measuring");
		this.calibrationSettings = calibrationSettings;
		this.alignmentMatrix = alignmentMatrix;
		this.lineTracker = new LineTracker(5);
		this.intersections = new HashMap<>();
		this.segments = new HashMap<>();
	}

	public boolean isCalibrated(){
		return calibrationSettings != null;
	}

	public boolean isAligned(){
		return alignmentMatrix != null;
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){

		super.populateControls(app, components);

		components.add(FlexiweldApp.createButton("\u25a9 " + (isCalibrated() ? "Recalibrate" : "Calibrate"), e -> prepareCalibration(app)));
		components.add(FlexiweldApp.createButton("\u25a3 " + (isAligned() ? "Realign" : "Align"), e -> prepareAlignment(app)));

		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Lines", 		Color.BLUE, 	showLines, 			e -> showLines 			= !showLines));
		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Centrelines", 	Color.CYAN, 	showCentrelines, 	e -> showCentrelines 	= !showCentrelines));
		components.add(FlexiweldApp.createFancyToggleButton("\u2014 Segments", 		Color.WHITE, 	showSegments, 		e -> showSegments 		= !showSegments));
		components.add(FlexiweldApp.createFancyToggleButton("\u2795 Corners", 		Color.YELLOW, 	showIntersections, 	e -> showIntersections 	= !showIntersections));
		components.add(FlexiweldApp.createFancyToggleButton("\u2aa6 Angles", 		Color.GREEN, 	showAngles, 		e -> showAngles 		= !showAngles));
	}

	private void prepareCalibration(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the calibration parameters below
		app.setMode(new CalibrationMode(new Size(9, 6), 25.5, calibrationSettings, alignmentMatrix));
	}

	private void prepareAlignment(FlexiweldApp app){
		// TODO: Dialogue box that prompts the user for the alignment parameters below
		app.setMode(new AlignmentMode(new Size(9, 6), 25.5, calibrationSettings, alignmentMatrix));
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
			frame = calibrationSettings.undistort(frame); // Lens correction
		}

		// "Image space" refers to coordinates in the undistorted camera frame in pixels, with no other processing
		// "World space" refers to actual coordinates in the world, in millimetres (assuming objects are in-plane)
		// "Screen space" refers to coordinates on the screen in pixels, as displayed

		// alignmentMatrix transforms *image* space to *world* space
		// VideoFeed#transformForDisplay transforms *image* space to *screen* space

		intersections.clear();
		segments.clear();

		// Perform geometry processing in the (undistorted) image space because it's quicker since it has fewer pixels,
		// and because we won't gain any accuracy by scaling first - accuracy is still limited by the camera resolution

		averagedLines = lineTracker.processNextFrame(frame);

		centrelines = Utils.findCentrelines(averagedLines, WIDTH_THRESHOLD, ANGLE_THRESHOLD);
		// Sort by angle for easier processing later
		centrelines.sort(Comparator.comparing(Line::angle).reversed());

		List<Intersection> intersectionList = new ArrayList<>();
		List<Line> segmentList = new ArrayList<>();

		Intersection.intersect(centrelines, intersectionList, segmentList);

		// Transform results into the world space to get the actual measurements, and store them in maps for later

		MatOfPoint2f dst = new MatOfPoint2f();

		Mat transform = isAligned() ? alignmentMatrix : Mat.eye(3, 3, CvType.CV_32F);

		if(!intersectionList.isEmpty()){

			Core.perspectiveTransform(Intersection.toMatrix(intersectionList), dst, transform);
			List<Intersection> transformedInts = Intersection.fromMatrix(dst);

			for(int i = 0; i < intersectionList.size(); i++){
				double angle = Line.acuteAngleBetween(transformedInts.get(i).getLineA(), transformedInts.get(i).getLineB());
				intersections.put(intersectionList.get(i), angle);
			}
		}

		// Technically we're transforming some points twice here, but that's a fairly inexpensive operation compared to
		// performing the intersections twice
		if(!segmentList.isEmpty()){

			Core.perspectiveTransform(Line.toMatrix(segmentList), dst, transform);
			List<Line> transformedLines = Line.fromMatrix(dst);

			for(int i = 0; i < segmentList.size(); i++){
				segments.put(segmentList.get(i), transformedLines.get(i).length());
			}
		}

		return frame;
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
				// If we're going to display coordinates, we need to store the original point
//				Imgproc.putText(frame, String.format("(%.2f, %.2f)", point.x, point.y), point,
//						Core.FONT_HERSHEY_PLAIN, 2, INTERSECTION_COLOUR, 2);
			}

			if(showAngles){

				// Sorting centrelines by angle earlier means line B always has the smaller angle
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
}
