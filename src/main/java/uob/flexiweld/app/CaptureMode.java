package uob.flexiweld.app;

import org.opencv.core.Mat;

import java.awt.*;
import java.util.List;

/**
 * Abstract base class for all capture modes. This class
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
	 * @param raw The raw video frame, as captured by the camera, with no distortion correction, scaling or other
	 *            modifications. Since some OpenCV methods modify images directly and others require a destination
	 *            matrix, implementors are free to decide whether to modify the raw frame - the {@link Mat} object will
	 *            not be used until the next frame, when it will be overwritten entirely.
	 * @return The resulting frame, after processing
	 */
	public abstract Mat processFrame(VideoFeed videoFeed, Mat raw);

}
