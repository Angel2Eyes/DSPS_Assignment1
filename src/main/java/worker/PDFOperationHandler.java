package worker;

import awsService.StorageService;
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

    public String work(String input_line, String job_name) throws IOException {
        System.out.printf("\nInitiating S3");
        StorageService s3 = new StorageService("bucket-dsps1");
        String output = "";
        System.out.printf("\nStarting work function");
        int tab_index = input_line.indexOf("\t");
        if (tab_index == -1){
            return "Input not valid: Missing tab char";
        }
        String operation = input_line.substring(0, tab_index);
        String pdf_url = input_line.substring(tab_index + 1);
        // Replacing spaces with %20:
        pdf_url = pdf_url.replace(" ", "%20");
        //
        System.out.printf("\nTrying to Read PDF file");
        try {
            // Get the PDF file from URL to local file
            File file = ReadPDFFromURL.Load(pdf_url);
            if (file.length() == 0) {
                System.out.printf("\nFile not exist");
                return "File is not found";
            }
            else {
                PDDocument doc = Loader.loadPDF(file);
                String file_name_no_suffix = file.getName().substring(0, file.getName().length() - 4);
                file_name_no_suffix = file_name_no_suffix.replace("%20", "-");
                if (operation.equals("ToText")) {
                    System.out.printf("\nChanging PDF to text");
                    PDFTextStripper pdfStripper = new PDFTextStripper();
                    pdfStripper.setStartPage(1);
                    pdfStripper.setEndPage(1);
                    String parsedText = pdfStripper.getText(doc);
                    PrintWriter pw = new PrintWriter(job_name + "-" + file_name_no_suffix + ".txt");
                    pw.print(parsedText);
                    pw.close();
                    s3.uploadFile(job_name + "-" + file_name_no_suffix + ".txt", job_name + "-" + file_name_no_suffix + ".txt");
                    output = "https://bucket-dsps1.s3.amazonaws.com/" + job_name + "-" + file_name_no_suffix + ".txt";
                } else {
                    if (operation.equals("ToHTML")) {
                        System.out.printf("\nChanging PDF to html");
                        PDFText2HTML converter = new PDFText2HTML(); // the converter
                        converter.setStartPage(1);
                        converter.setEndPage(1);
                        String html = converter.getText(doc); // That's it!
                        PrintWriter pw = new PrintWriter(job_name + "-" + file_name_no_suffix + ".html");
                        pw.print(html);
                        pw.close();
                        s3.uploadFile(job_name + "-" + file_name_no_suffix + ".html", job_name + "-" + file_name_no_suffix + ".html");
                        output = "https://bucket-dsps1.s3.amazonaws.com/" + job_name + "-" + file_name_no_suffix + ".html";
                    } else {
                        if (operation.equals("ToImage")) {
                            System.out.printf("\nChanging PDF to image");
                            PDFRenderer pdfRenderer = new PDFRenderer(doc);
                            BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);
                            ImageIOUtil.writeImage(bim, job_name + "-" + file_name_no_suffix + ".png", 300);
                            s3.uploadFile(job_name + "-" + file_name_no_suffix + ".png", job_name + "-" + file_name_no_suffix + ".png");
                            output ="https://bucket-dsps1.s3.amazonaws.com/" + job_name + "-" + file_name_no_suffix + ".png";
                        } else {
                            output = "Input not valid: Operation is not supported";
                        }
                    }
                }
                doc.close();
            }
        }
        catch(Exception e){
            return e.getMessage();
        }
        return output;
    }
}

