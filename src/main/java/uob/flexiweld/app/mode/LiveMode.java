package uob.flexiweld.app.mode;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import uob.flexiweld.app.FlexiweldApp;
import uob.flexiweld.app.VideoFeed;
import uob.flexiweld.util.Utils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.List;

/**
 * Base class for all capture modes that display the live video feed (everything except {@link StandbyMode}). This
 * class controls the buttons common to all modes (stop, pause and snapshot), as well as the status bar fps readout.
 * @author Finin Quincey
 */
public abstract class LiveMode extends CaptureMode {

	/** A file filter for JPEG images. */
	private static final FileNameExtensionFilter JPEG_FILTER = Utils.createExtensionFilter("JPEG images", "jpg", "jpeg", "jfif");

	/** Status bar label for the camera framerate readout. */
	private JLabel fpsReadout;

	/** Creates a new {@code LiveMode} with the given display name. */
	public LiveMode(String name){
		super(name);
	}

	@Override
	public void populateControls(FlexiweldApp app, List<Component> components){

		components.add(FlexiweldApp.createButton("\u23f9 Stop", e -> app.toggleCamera()));

		JToggleButton pauseButton = FlexiweldApp.createToggleButton("\u275a\u275a Pause", e -> app.getVideoFeed().togglePause());
		pauseButton.setSelected(app.getVideoFeed().isPaused());
		components.add(pauseButton);

		components.add(FlexiweldApp.createButton("\ud83d\udcf7 Snapshot", e -> takeSnapshot(app)));

		JToggleButton mirrorButton = FlexiweldApp.createToggleButton("\u25e7 Mirror", e -> app.getVideoFeed().toggleMirror());
		mirrorButton.setSelected(app.getVideoFeed().isMirrored());
		components.add(mirrorButton);
	}

	@Override
	public void populateStatusBar(List<Component> components){
		super.populateStatusBar(components);
		fpsReadout = FlexiweldApp.addStatusText("", components);
	}

	@Override
	public Mat annotateFrame(VideoFeed videoFeed, Mat frame){
		fpsReadout.setText(String.format("%.4g fps", videoFeed.getFps()));
		return frame; // Do nothing to the frame
	}

	/** Captures the current frame and prompts the user to select a location to save it as a JPEG image. */
	private void takeSnapshot(FlexiweldApp app){
		app.getVideoFeed().pauseFor(1000); // Pause for a bit so the user can briefly see what was captured
		Mat snapshot = app.getVideoFeed().getCurrentFrame(); // Record the current frame
		String path = app.saveWithOverwriteWarning(JPEG_FILTER); // Prompt the user to select a location and filename
		if(path != null) Imgcodecs.imwrite(path, snapshot); // Write the image to the selected file
	}

}
