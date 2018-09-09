package sample;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;

public class Controller
{
    @FXML private Button recordButton;
    @FXML private Button processButton;
    @FXML private Text recordDuration;
    @FXML private Text processDuration;
    @FXML private ProgressBar progressBar;
    private int recordTime = 0;
    private double processTime = 0;
    static String error = "";
    static boolean uhoh = false;
    static boolean listening = false;

    @FXML
    private void processButtonPressed()
    {
        if (processButton.getText().equals("Process"))
        {
            if (recordButton.getText().equals("Stop"))
            {
                Main.record = false;
                recordButton.setText("Record");
                zzz(200);
            }
            System.out.println("checkin frames");
            Main.checkFrames();
            System.out.println("finished checkin those frames boss");
            Main.process = true;
            new Thread(Main::process).start();
            new Thread(this::checkProgress).start();
            processButton.setText("Processing...");
            processTime = 0;
            processDuration.setText("");
            progressBar.setProgress(0);
            progressBar.setOpacity(1);
        }
    }

    @FXML
    private void recordButtonPressed()
    {
        if (!listening)
        {
            listening = true;
            (new Thread(this::listen)).start();
        }

        if (recordButton.getText().equals("Record"))
        {
            Main.record = true;
            if (processButton.getText().equals("Process"))
                new Thread(() -> Main.record("ScreenRecord/record")).start();
            else
                new Thread(() -> Main.record("ScreenRecord/record2")).start();
            new Thread(this::incrementDuration).start();
            recordButton.setText("Stop");
            recordTime = 0;
            recordDuration.setText(recordTime / 60 + ":" + ((recordTime % 60 < 10) ? "0" : "") + recordTime % 60);
        }
        else
        {
            Main.record = false;
            recordButton.setText("Record");
        }
    }

    private void incrementDuration()
    {
        while (Main.record)
        {
            zzz(1000);
            recordTime++;
            recordDuration.setText(recordTime / 60 + ":" + ((recordTime % 60 < 10) ? "0" : "") + recordTime % 60);
        }
    }

    private void checkProgress()
    {
        while (Main.process)
        {
            zzz(300);
            processTime += 0.3;
            double fractionComplete = ((double) Main.framesProcessed) / Main.framesToProcess;
            int secondsLeft = (int) (processTime * ((1 - fractionComplete) / fractionComplete));
            if (secondsLeft > 1000000)
                processDuration.setText("Heat death\n of universe :(");
            else if (secondsLeft < 2)
                processDuration.setText("Just a second!");
            else
                processDuration.setText("~" + secondsLeft / 60 + ":" + ((secondsLeft % 60 < 10) ? "0" : "") + secondsLeft % 60 + " left");
            progressBar.setProgress(fractionComplete);
        }

        Platform.runLater(() -> processButton.setText("Process"));
        Platform.runLater(() -> processDuration.setText("Done!"));
    }

    private void zzz(int sleep)
    {
        try
        {
            Thread.sleep(sleep);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void listen()
    {
        while (true)
        {
            zzz(1000);
            if (uhoh)
                recordDuration.setText(System.getProperty("user.dir"));
        }
    }
}
