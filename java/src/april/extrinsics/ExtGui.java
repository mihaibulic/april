package april.extrinsics;

import java.io.File;
import java.util.ArrayList;
import javax.swing.JFrame;
import april.jcam.ImageSource;

public class ExtGui extends JFrame
{
    public ExtGui()
    {
        super("Ext cal");

        String configPath = System.getenv("APRIL_CONFIG");
        configPath += (!configPath.endsWith(File.separator) ? File.separator : "") + "camera.config";
        ArrayList<String> urls = ImageSource.getCameraURLs();

        NewExtrinsicsPanel nep = new NewExtrinsicsPanel("");
        nep.go(configPath, urls.toArray(new String[urls.size()]));
        add(nep);
    }

    public static void main(String[] args)
    {
        new ExtGui();
    }
}
