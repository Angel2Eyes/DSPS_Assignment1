package worker;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;

public class ReadPDFFromURL {
    public static File Load(String url) throws IOException {

        URL url1 = new URL(url);
        String filename = FilenameUtils.getBaseName(url1.getPath());
        byte[] ba1 = new byte[1024];
        int baLength;
        File file = new File(filename + ".pdf");
        FileOutputStream fos1 = new FileOutputStream(file);

        try {
            // Contacting the URL
            System.out.print("Connecting to " + url1.toString() + " ... ");
            URLConnection urlConn = url1.openConnection();

            // Checking whether the URL contains a PDF
            if (!urlConn.getContentType().equalsIgnoreCase("application/pdf")) {
                System.out.println("FAILED - This is not a PDF");
            } else {
                try {
                    // Read the PDF from the URL and save to a local file
                    InputStream is1 = url1.openStream();
                    while ((baLength = is1.read(ba1)) != -1) {
                        fos1.write(ba1, 0, baLength);
                    }
                    fos1.flush();
                    fos1.close();
                    is1.close();

                } catch (ConnectException ce) {
                    System.out.println("FAILED.\n[" + ce.getMessage() + "]\n");
                }
            }

        } catch (NullPointerException npe) {
            System.out.println("FAILED.\n[" + npe.getMessage() + "]\n");
        }
        finally {
            return file;
        }
    }
}
