package worker;

import awsService.SimpleQueueService;

import awsService.StorageService;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.*;
import java.util.*;

public class Worker implements Runnable {

    private static String sqs_to_manager_name;
    private static String sqs_from_manager_name;
    private static PDFOperationHandler handler;

    public static void main(String[] args) throws IOException, ParseException {
        // Get the names of the AWS instances
        BufferedReader services_buffer = new BufferedReader(new FileReader("services-worker"));
        String line = services_buffer.readLine();
        services_buffer.close();

        JSONParser parser = new JSONParser();
        Object o = parser.parse(line);
        JSONObject jsonObj = (JSONObject) o;

        // Get the AWS instances' names
        sqs_to_manager_name = (String) jsonObj.get("sqs-to-manager");
        sqs_from_manager_name = (String) jsonObj.get("sqs-from-manager");

        // Initialize the Review Analysis Handler
        handler = new PDFOperationHandler();
        System.out.printf("\nInitialized PDF handler");

        // Start Worker's threads
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            Thread t = new Thread(new Worker());
            t.start();
            threads.add(t);
        }

        // We run 5 threads simultaneously to maximize working speed.
        // Every time a thread fails, we start another one.
        // If a thread indeed fails, the job it was doing will be handled by another thread or another computer,
        // because it wouldn't have been deleted.
        // If the error is due to a QueueDoesNotExistException that means the manager has failed hence terminate the worker.
        while (!threads.isEmpty()) {
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (QueueDoesNotExistException e) {
                    System.err.printf("Error in thread %s: Queue does not exist!", t.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    threads.remove(t);
                    Thread newThread = new Thread(new Worker());
                    newThread.start();
                    threads.add(newThread);
                }
            }
        }
    }

    @Override
    public void run() {
        System.out.printf("\nWorker Thread Starting...");
        // Initialize the AWS SQS instances
        SimpleQueueService sqs_to_manager = new SimpleQueueService(sqs_to_manager_name);
        SimpleQueueService sqs_from_manager = new SimpleQueueService(sqs_from_manager_name);

        while (true) {
            // Fetch the next pending task (Review)
            List<Message> jobs = sqs_from_manager.nextMessages(600, 10);
            System.out.printf("\nReceived " + jobs.size() + " Jobs");
            for (Message job : jobs) {
                String job_name = job.messageAttributes().get("Name").stringValue();
                String sender = job.messageAttributes().get("Sender").stringValue();
                System.out.printf("\nJob Received: %s\tFrom: %s\n", job_name, sender);

                String result;
                try {
                    String[] input_line_arr = job.body().split("\t");
                    System.out.printf("\ninitiating handler.work for " + job_name);
                    String output = handler.work(job.body(), job_name);
                    System.out.printf("\nfinished handler.work of " + job_name);
                    JSONObject report = new JSONObject();
                    report.put("origin", input_line_arr[1]);
                    report.put("operation", input_line_arr[0]);
                    report.put("changed", output);
                    result = report.toJSONString();
                    System.out.printf("\ncreated report of " + job_name);

                } catch (IOException e) {
                    throw new RuntimeException("\nThe job " + job_name + " failed...\nError: " + e.getMessage());
                }

                // Send a message with the result
                assert result != null;
                System.out.printf("\nsending result of " + job_name);
                sendResult(sqs_to_manager, job_name, sender, result);

                // Remove the executed task from the queue
                sqs_from_manager.deleteMessage(job);
                System.out.println("\nJob Completed: " + job_name);
            }
        }
    }

    // Because we don't want two threads to send a message on the queue at the same moment, we synchronize the function
    private void sendResult(SimpleQueueService sqs_to_manager, String job_name, String sender, String result) {
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        MessageAttributeValue nameAttribute = MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("Job completed:\t" + job_name + "\tFrom: " + sender)
                .build();
        MessageAttributeValue reportAttribute = MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(sender)
                .build();
        MessageAttributeValue doneAttribute = MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("Job Completed")
                .build();
        messageAttributes.put("Name", nameAttribute);
        messageAttributes.put("Sender", reportAttribute);
        messageAttributes.put("Type", doneAttribute);

        sqs_to_manager.sendMessage(SendMessageRequest.builder()
                .messageBody(result)
                .messageAttributes(messageAttributes)
        );
    }

    public static String getUserData(String bucket) {
        String cmd = "#! /bin/bash" + '\n' +
                "sudo wget -O services-worker https://" + bucket + ".s3.amazonaws.com/services-worker" + '\n' +
                "sudo wget https://" + bucket + ".s3.amazonaws.com/Worker.jar" + '\n' +
                "sudo java -jar Worker.jar > logger" + '\n';
        return Base64.getEncoder().encodeToString(cmd.getBytes());
    }
}
