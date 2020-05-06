package uob.flexiweld.app.mode;

import org.opencv.core.Mat;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;

import java.awt.*;
import java.util.List;

/**
 * Abstract base class for all capture modes. One instance of this class is active at any one time; this instance is
 * stored in {@code FlexiweldApp#mode}. Capture modes are responsible for:
 * <p></p>
 * - Defining the buttons displayed on the control panel, via {@link CaptureMode#populateControls(FlexiweldApp, List)}<br>
 * - Defining the buttons displayed on the control panel, via {@link CaptureMode#populateStatusBar(List)}<br>
 * - Performing the necessary processing steps on each raw frame of the video feed, via
 * {@link CaptureMode#processFrame(VideoFeed, Mat)}<br>
 * - Adding any required annotations or post-processing to each processed, scaled frame of the video feed, via
 * {@link CaptureMode#annotateFrame(VideoFeed, Mat)}<br>
 * @author Finin Quincey
 */
// N.B. If we're getting technical, this is the *state interface* part of a *state pattern*.
// This is an abstract class rather than an interface because certain things are shared by all modes
public abstract class CaptureMode {

	public final String name;

	public CaptureMode(String name){
		this.name = name;
	}

	/**
	 * Populates the given list of {@link Component}s for display in the app control panel. This method is called each
	 * time the capture mode is changed via {@link FlexiweldApp#setMode(CaptureMode)}. By convention, subclasses should
	 * call super before adding any of their own components (this ensures the control panel elements don't 'jump around').
	 * @param app The {@link FlexiweldApp} instance, for reference/callbacks
	 * @param components The list of components to be populated
	 */
	public void populateControls(FlexiweldApp app, List<Component> components){
		// Nothing here because there are no buttons common to all modes!
	}

	/**
	 * Populates the given list of {@link Component}s for display in the app status bar. This method is called each
	 * time the capture mode is changed via {@link FlexiweldApp#setMode(CaptureMode)}. By convention, subclasses should
	 * call super before adding any of their own components (this ensures the status bar elements don't 'jump around').
	 * @param components The list of components to be populated
	 */
	public void populateStatusBar(List<Component> components){
		FlexiweldApp.addStatusText(name, components);
	}

	/**
	 * Processes the given raw video frame and returns the result.
	 * @param videoFeed The video feed object calling this method, for reference.
	 * @param frame The raw video frame, as captured by the camera, with no distortion correction, scaling or other
	 *            modifications. Since some OpenCV methods modify images directly and others require a destination
	 *            matrix, implementors are free to decide whether to modify this parameter. The {@link Mat} object
	 *            itself will be overwritten next frame, so if it is to be stored, a copy should be made first.
	 * @return The resulting frame, after processing
	 */
	public abstract Mat processFrame(VideoFeed videoFeed, Mat frame);

	/**
	 * Adds annotations to the given processed video frame and returns the result. Updating of controls and other GUI
	 * elements should also be done in this method (not {@link CaptureMode#processFrame(VideoFeed, Mat)}) to ensure
	 * everything is processed before updating the controls.
	 * @param videoFeed The video feed object calling this method, for reference.
	 * @param frame The processed video frame, after scaling, flipping, distortion correction and other
	 *            modifications. Since some OpenCV methods modify images directly and others require a destination
	 *            matrix, implementors are free to decide whether to modify this parameter. The {@link Mat} object
	 *            itself will be overwritten next frame, so if it is to be stored, a copy should be made first.
	 * @return The resulting frame, after annotations have been drawn
	 */
	public abstract Mat annotateFrame(VideoFeed videoFeed, Mat frame);

}
