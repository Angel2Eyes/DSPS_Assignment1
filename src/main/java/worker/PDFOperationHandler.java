package worker;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.PDFBox;
import org.apache.pdfbox.tools.PDFText2HTML;
import org.apache.pdfbox.tools.PDFToImage;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.javatuples.Pair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

public class PDFOperationHandler {
    public PDFOperationHandler() {

    }

    public String work(String input_line) throws IOException { // TODO
        int tab_index = input_line.indexOf("\t");
        if (tab_index == -1){
            return "Input not valid: Missing tab char";
        }
        String operation = input_line.substring(0, tab_index);
        String pdf_url = input_line.substring(tab_index + 1);
        try {
            // Get the PDF file from URL to local file
            File file = ReadPDFFromURL.Load(pdf_url);
            if (file.length() == 0) {
                return "<" + operation + ">: " + pdf_url  + " <File is not found>";
            }
            else {
                PDDocument doc = Loader.loadPDF(file);
                String file_name_no_suffix = file.getName().substring(0, file.getName().length() - 1 - 4);
                if (operation.equals("ToText")) {
                    PDFTextStripper pdfStripper = new PDFTextStripper();
                    pdfStripper.setStartPage(1);
                    pdfStripper.setEndPage(1);
                    String parsedText = pdfStripper.getText(doc);
                    PrintWriter pw = new PrintWriter(file_name_no_suffix + ".txt");
                    pw.print(parsedText);
                    pw.close();
                } else {
                    if (operation.equals("ToHTML")) {
                        PDFText2HTML converter = new PDFText2HTML(); // the converter
                        converter.setStartPage(1);
                        converter.setEndPage(1);
                        String html = converter.getText(doc); // That's it!
                        PrintWriter pw = new PrintWriter(file_name_no_suffix + ".html");
                        pw.print(html);
                        pw.close();
                    } else {
                        if (operation.equals("ToImage")) {
                            PDFRenderer pdfRenderer = new PDFRenderer(doc);
                            BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
                            ImageIOUtil.writeImage(bim, file_name_no_suffix + ".png", 300);
                        } else {
                            return "Input not valid: Operation is not supported";
                        }
                    }
                }
                doc.close();
            }
        }
        catch(Exception e){
            return "<" + operation + ">: " + pdf_url + " <" + e.getMessage() + ">";
        }
        return "<" + operation + ">: " + pdf_url; // + output address TODO
        // TODO upload to S3
    }
}

