import java.io.*;
import java.util.*;
import fbk.hlt.parsing.CGTDocumentInterface;

/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 04-apr-2013
 */

public class TextproParser implements CGTDocumentInterface {
    static String TEXTPRO_PATH = null;

    public TextproParser() {
        try {
            //load the TextPro home dir path from the file ./conf/textpro.properties
            Properties properties = new Properties();
            properties.load(new FileInputStream("./conf/textpro.properties"));
            TEXTPRO_PATH = properties.getProperty("textpropath");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public LinkedHashMap getHeader(String url, String text, LinkedHashMap header) {
        return null;
    }

    public String getBody(String url, String text, LinkedHashMap header) {
        if (text != null && text.length() > 0) {
            File tmpFile = new File("/tmp/aa");
            OutputStreamWriter out = null;
            try {
                // write the input 
                out = new OutputStreamWriter(new FileOutputStream(tmpFile), "UTF8");
                out.write(text);
                out.close();
                
                // run TextPro
                String[] CONFIG = {"TEXTPRO=" + TEXTPRO_PATH, "PATH=" + "/usr/bin/" + ":."};
                String[] cmd = {"/bin/tcsh", "-c", "perl " + TEXTPRO_PATH + "/textpro.pl -l eng -y "+tmpFile};
                Process process = run(cmd, CONFIG);
                process.waitFor();

                //read the TextPro's output
                BufferedReader txpFile = new BufferedReader (new InputStreamReader (new FileInputStream (tmpFile.getCanonicalPath() + ".txp")));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = txpFile.readLine()) != null) {
                    result.append(line).append("\n");
                }
                txpFile.close();

                return result.toString();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /** Runs executable command
     * @param command
     * @param config the configuration setting
     * @exception IOException
     */
    private Process run(String[] command, String[] config) throws IOException {
        try {
            Runtime rt = Runtime.getRuntime();
            return rt.exec(command, config);

        } catch(Exception e) {
            throw new IOException("Run process error: " + e.getMessage());
        }

    }
}



