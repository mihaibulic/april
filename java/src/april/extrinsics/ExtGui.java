package april.extrinsics;

import java.io.File;
import javax.swing.JFrame;

public class ExtGui extends JFrame
{
    private static final long serialVersionUID = 1L;

    public ExtGui()
    {
        super("Ext cal");

        String configPath = System.getenv("APRIL_CONFIG");
        configPath += (!configPath.endsWith(File.separator) ? File.separator : "") + "camera.config";

        NewExtrinsicsPanel nep = new NewExtrinsicsPanel("");
        nep.go(configPath);
        add(nep);
    }

    public static void main(String[] args)
    {
        new ExtGui();
    }
}
