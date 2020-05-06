package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.List;

public abstract class LiveMode extends CaptureMode {

	private final FileNameExtensionFilter jpegFilter = new FileNameExtensionFilter("JPEG images (*.jpg, *.jpeg, *.jfif)", "jpg", "jpeg", "jfif");

	private JLabel fpsReadout;

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
		app.getVideoFeed().pauseFor(1000);
		Mat snapshot = app.getVideoFeed().getCurrentFrame();
		String path = app.saveWithOverwriteWarning(jpegFilter);
		if(path != null) Imgcodecs.imwrite(path, snapshot);
	}

}
