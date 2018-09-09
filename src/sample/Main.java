package sample;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class Main extends Application
{
    static boolean record = false, process = false;

    static int framesToProcess;
    static int framesProcessed;

    private static final int detail = 8000;

    private static int width = 1600;
    private static int height = 900;
    private static int sobelWidth = 1602;
    private static int sobelHeight = 902;

    private static boolean[][][] alphabet = new boolean[97][6][11];
    private static int[][] drawnAlphabet = new int[97][66];
    private static int[] greyscale = new int[width * height];
    private static boolean[][] sobel = new boolean[sobelWidth][sobelHeight];

    private static final Dimension screenBounds = Toolkit.getDefaultToolkit().getScreenSize();
    private static Robot robot = null;

    @Override
    public void start(Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 300, 275));
        primaryStage.show();

        primaryStage.getScene().getStylesheets().add(Main.class.getResource("ScreenRecord/Stylesheet.css").toExternalForm());

        initialize();
    }

    static void checkFrames()
    {
        IContainer container = IContainer.make();
        container.open("ScreenRecord/record.mp4", IContainer.Type.READ, null);

        IStreamCoder videoCoder = container.getStream(0).getStreamCoder();
        videoCoder.open();

        IPacket packet = IPacket.make();

        framesToProcess = -4;

        while(container.readNextPacket(packet) >= 0)
            framesToProcess++;

        videoCoder.close();
        videoCoder.delete();
        container.close();
    }

    static void process()
    {
        framesProcessed = 0;

        IMediaWriter writer = ToolFactory.makeWriter("ScreenRecord/regular.mp4");
        writer.addVideoStream(0, 0, ICodec.ID.CODEC_ID_MPEG4, screenBounds.width, screenBounds.height);

        IContainer container = IContainer.make();
        container.open("ScreenRecord/record.mp4", IContainer.Type.READ, null);

        IStreamCoder videoCoder = container.getStream(0).getStreamCoder();
        videoCoder.open();

        IVideoResampler resampler = null;

        if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24)
        {
            resampler = IVideoResampler.make(
                    videoCoder.getWidth(), videoCoder.getHeight(), IPixelFormat.Type.BGR24,
                    videoCoder.getWidth(), videoCoder.getHeight(), videoCoder.getPixelType());
        }

        BufferedImage temp;
        IPacket packet = IPacket.make();

        int counter = 0;
        long time = System.nanoTime();

        width = videoCoder.getWidth();
        height = videoCoder.getHeight();

        if (width % 6 == 0)
            sobelWidth = width;
        else
            sobelWidth = width + (6 - (width % 6));

        if (height % 11 == 0)
            sobelHeight = height;
        else
            sobelHeight = height + (11 - (height % 11));

        while(container.readNextPacket(packet) >= 0)
        {
            IVideoPicture picture = IVideoPicture.make(videoCoder.getPixelType(), videoCoder.getWidth(), videoCoder.getHeight());

            System.out.println(videoCoder.getWidth() + "  " + videoCoder.getHeight());

            int offset = 0;
            while(offset < packet.getSize())
            {
                int bytesDecoded = videoCoder.decodeVideo(picture, packet, offset);
                offset += bytesDecoded;

                counter++;

                if (!picture.isComplete())
                    continue;

                IVideoPicture newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
                resampler.resample(newPic, picture);

                BufferedImage javaImage = Utils.videoPictureToImage(newPic);
                javaImage = getTextImage(javaImage);

                temp = new BufferedImage(javaImage.getWidth(), javaImage.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                temp.getGraphics().drawImage(javaImage, 0, 0, null);

                writer.encodeVideo(0, temp, picture.getPts(), TimeUnit.MICROSECONDS);

                framesProcessed++;
            }
        }

        System.out.println("processed " + counter + " frames in just " + (((double) (System.nanoTime() - time)) / 1000000000) + " seconds :))");

        videoCoder.close();
        videoCoder.delete();
        writer.close();
        container.close();

        process = false;
    }

    static void record(String filePath)
    {
        IMediaWriter writer = ToolFactory.makeWriter(filePath + ".mp4");
        writer.addVideoStream(0, 0, ICodec.ID.CODEC_ID_MPEG4, screenBounds.width, screenBounds.height);
        Rectangle captureSize = new Rectangle(screenBounds);

        BufferedImage image;
        BufferedImage temp;

        long startTime = System.nanoTime();

        for (long currentTime = System.nanoTime(); record; currentTime = System.nanoTime())
        {
            image = robot.createScreenCapture(captureSize);
            temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            temp.getGraphics().drawImage(image, 0, 0, null);

            writer.encodeVideo(0, temp, currentTime - startTime, TimeUnit.NANOSECONDS);
        }

        writer.close();

        System.out.println("done recording");
    }

    static BufferedImage getTextImage(BufferedImage input)
    {
        BufferedImage outputImage;

        byte[] rawGreyscale = ((DataBufferByte) input.getRaster().getDataBuffer()).getData();

        //greyscale
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                greyscale[i + width * j] = (rawGreyscale[3 * (i + width * j)] + rawGreyscale[3 * (i + width * j) + 1] + rawGreyscale[3 * (i + width * j) + 2]) / 3;

        //sobel filter
        for (int i = 1; i < width - 1; i++)
        {
            int a = greyscale[i - 1];
            int b = greyscale[i - 1 + width];
            int c = greyscale[i - 1 + width + width];

            int d = greyscale[i];
            int e = greyscale[i + width + width];

            int f = greyscale[i + 1];
            int g = greyscale[i + 1 + width];
            int h = greyscale[i + 1 + width + width];

            for (int j = 1; j < height - 1; j++)
            {
                int Gx = a + (2 * d) + f - c - (2 * e) - h;
                Gx *= Gx;
                int Gy = a + 2 * b + c - f - 2 * g - h;
                Gy *= Gy;

                int color = Gx + Gy;

                sobel[i - 1][j - 1] = color >= detail;

                if (j != height - 2)
                {
                    a = b;
                    b = c;
                    f = g;
                    g = h;
                    d = greyscale[i + width * j];
                    c = greyscale[(i - 1) + width * (j + 2)];
                    e = greyscale[i + width * (j + 2)];
                    h = greyscale[(i + 1) + width * (j + 2)];
                }
            }
        }

        outputImage = new BufferedImage(sobelWidth, sobelHeight, BufferedImage.TYPE_INT_RGB);
        int[] imageData = ((DataBufferInt) outputImage.getRaster().getDataBuffer()).getData();

        int grey = -13948117;

        //if (type.equals("regular"))
            for (int i = 0; i < sobelHeight; i += 11)
            {
                for (int j = 0; j < sobelWidth; j += 6)
                {
                    int maxScore = 0;
                    int index = 0;
                    for (int k = 0; k < 97; k++)
                    {
                        int tempScore = 0;
                        for (int l = 0; l < 6; l++)
                            for (int m = 0; m < 11; m++)
                                if (sobel[j + l][i + m] == alphabet[k][l][m])
                                    tempScore++;

                        if (tempScore > maxScore)
                        {
                            index = k;
                            maxScore = tempScore;
                        }

                        if (tempScore == 66)
                            break;
                    }

                    for (int l = 0; l < 6; l++)
                        for (int m = 0; m < 11; m++)
                            imageData[(i + m) * sobelWidth + j + l] = drawnAlphabet[index][l * 11 + m];
                }
            }
        /*else if (type.equals("color"))
            for (int i = 0; i < sobelHeight; i += 11)
            {
                for (int j = 0; j < sobelWidth; j += 6)
                {
                    int maxScore = 0;
                    int index = 0;
                    for (int k = 0; k < 97; k++)
                    {
                        int tempScore = 0;
                        for (int l = 0; l < 6; l++)
                            for (int m = 0; m < 11; m++)
                                if (sobel[j + l][i + m] == alphabet[k][l][m])
                                    tempScore++;

                        if (tempScore > maxScore)
                        {
                            index = k;
                            maxScore = tempScore;
                        }

                        if (tempScore == 66)
                            break;
                    }

                    for (int l = 0; l < 6; l++)
                        for (int m = 0; m < 11; m++)
                        {
                            int color = drawnAlphabet[index][l * 11 + m];
                            if (color == grey)
                                imageData[(i + m) * sobelWidth + j + l] = 0xFF000000;
                            else
                            {
                                if (width > j + l && height > i + m)
                                    imageData[(i + m) * sobelWidth + j + l] = input.getRGB(j + l, i + m);
                                else
                                    imageData[(i + m) * sobelWidth + j + l] = 0xFF000000;
                            }
                        }
                }
            }*/

        return outputImage;
    }

    private void initialize()
    {
        BufferedImage alphabetImage;
        BufferedImage drawnLettersImage;

        try
        {
            drawnLettersImage = ImageIO.read(new File("ScreenRecord/drawnalphabet.png"));

            for (int i = 0; i < 97; i++)
                for (int j = 0; j < 6; j++)
                    for (int k = 0; k < 11; k++)
                        drawnAlphabet[i][j * 11 + k] = drawnLettersImage.getRGB((i * 6) + j, k);

            alphabetImage = ImageIO.read(new File("ScreenRecord/alphabet.png"));

            for (int i = 0; i < 97; i++)
                for (int j = 0; j < 6; j++)
                    for (int k = 0; k < 11; k++)
                        alphabet[i][j][k] = (alphabetImage.getRGB((i * 6) + j, k) & 0x1) == 1;

            robot = new Robot();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Controller.uhoh = true;
            Controller.error = e.toString();
        }
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
