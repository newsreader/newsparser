import java.util.*;
import fbk.hlt.parsing.CGTDocumentInterface;

/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 2-mag-2013
 */

public class TestParser implements CGTDocumentInterface {

    public LinkedHashMap getHeader(String url, String text, LinkedHashMap header) {
        return null;
    }

    public String getBody(String url, String text, LinkedHashMap header) {
        String annotation = "";
        if (header != null)
            annotation += "HEADER METADATA: " + header.size() + "\n";
        else
            annotation += "NO HEADER\n";

        if (text != null)
            annotation += "TEXT LENGTH: " + String.valueOf(text.length()) + " chars\n";
        else
            annotation += "NO TEXT\n";

        return annotation;
    }

}

