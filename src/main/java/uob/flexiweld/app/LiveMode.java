package uob.flexiweld.app;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public abstract class LiveMode extends CaptureMode {

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
	public Mat processFrame(VideoFeed videoFeed, Mat raw){
		fpsReadout.setText(String.format("%.4g fps", videoFeed.getFps()));
		return raw; // Do nothing
	}

	private void takeSnapshot(FlexiweldApp app){

		app.getVideoFeed().pauseFor(1000);

		Mat snapshot = app.getVideoFeed().getCurrentFrame();

		JFileChooser imageExporter = new JFileChooser();
		FileNameExtensionFilter filter = new FileNameExtensionFilter("JPEG images", "jpg", "jpeg", "jfif");
		imageExporter.setFileFilter(filter);

		int result = imageExporter.showSaveDialog(app.getFrame());

		if(result == JFileChooser.APPROVE_OPTION){

			File file = imageExporter.getSelectedFile();
			String path = file.getPath();

			if(Arrays.stream(filter.getExtensions()).noneMatch(s -> file.getName().endsWith(s))){
				path = path + ".jpg";
			}

			Imgcodecs.imwrite(path, snapshot);
		}
	}

}
