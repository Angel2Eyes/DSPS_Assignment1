# DSPS - Assignment 1

First assignment in the course DSPS 2022A - Introduction to AWS in Java

Yuval Saar - 304999261
Ilay Nuriel - 312538580

1. [Description](#description)
2. [Run + Results](#run--results)
2. [EC2 parameters](#ec2-parameters)
2. [AWS Services Used](#aws-services-used)
3. [Scalability](#scalability)
4. [Issues encountered](#issues-encountered)
5. [Mandatory Requirements](#mandatory-requirements)

## Description
In this assignment we coded a real-world application to distributively process a list of PDF files,
perform some operations on them, and display the result on a web page.
The application is composed of a local application and instances running on the Amazon cloud. 
The application receives as an input a text file containing a list of URLs of PDF files with an operation to
perform on them. 
Then, instances will be launched in AWS (workers). 
Each worker will download PDF files, perform the requested operation, and send the results of the operations to the manager.

To do so, we developed three levels:
1. Local Application - Application that sends PDFs to be processed and format the output address into an HTML file (Runs Locally)
2. Manager - Receives the PDFs, dispatch them to different workers and collect the results before sending them to the local app (Run on an AWS instance)
3. Worker - Receives PDFs from the manager, process them (according to the operation) and send the report back to the manager (Run on an AWS instance)

## Run & Results

To run a Local Application:
```bash
java -jar LocalApplication.jar <input_file_without_suffix> <output_file> N [Terminate]
```
Where: 
- _LocalApplication.jar_ is the compiled `jar` of the LocalApplication.java file.
- input_file is the name of an input file (without extension) located in `Input_Files/<input_file>.txt` (the Input_Files directory must be in the same directory as the LocalApplication.jar)
- output_file is the name of an output file (without extension) where the report will be written (The extension will always be `.html`)
- N is the number of PDFs per worker
- Terminate is an __optional__ argument that means that once the task is over, all the instances must be shut down.

### Results - 

You can find the outputs of the 2 input files in the directory `Output_Files`.
Processing the bigger input file (2500 PDFs links) took __12 min and 31 seconds__ (taking into account that starting EC2 
instances takes time as well as initializing the workers (~1 minute)).
When Manager and Worker jar files were already on the S3 storage the running time is faster.

## EC2 parameters

### Type

|      Type      |  vCPUs | Memory (GiB) |
| :------------: | :----: | :----------: | 
|__T2_MICRO__    | 1      | 1            |
|__T2_SMALL__    | 1      | 2            |
|__T2_MEDIUM__   | 2      | 4            |
|__T2_LARGE__    | 2      | 8            |

In order to know which type to chose we tested several combinations:

#### Manager

The Manager node  isn't suppose to have much work. It serves as a connection between the Local Application
and the Workers. In our design, the Manager runs two threads:

* Thread 1: Deals with the tasks request from the Local Application and the creation of new workers accordingly.
* Thread 2: Creates the reports for each task by collecting the responses from the workers.

At our scale a `T2_MICRO` instance would have been enough. However, in order to make our system scalable, we had to
increase the Memory and decided to go for a `T2_SMALL` EC2 instance.

#### Worker

The Worker nodes are supposed to do the actual processing of the system. In order to complete a job,
a worker needs to use the `PDFBox` dependency which requires a  larger amount of memory than the manager.   
We started testing the system with smaller requirements such as T2_SMALL, 
TODO:
However, for both we ran into
`Out of Memory` exceptions from the workers when the PDF they had to process was too large. Finally, with a `T2_LARGE`
instance, the workers were able to easily process every kind of PDFs.

### WGET command on EC2

The wget utility is an HTTP and FTP client that allows downloading public objects from Amazon S3. It is installed by
default in Amazon Linux. To download an Amazon S3 object, use the following command, substituting the URL of the object
to download:

```bash 
wget https://my_bucket.s3.amazonaws.com/path-to-file
```  

The object need to be __public__; if the object is not public, an __`ERROR 403: Forbidden`__
message will be thrown. To change a file permissions to public, open the Amazon S3 console:  
[For more information](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AmazonS3.html)

## AWS Services Used
> "It is up to you to decide how many queues to use and how to split the jobs among the workers, but, and you will be graded accordingly,
> your system should strive to work in parallel. It should be as efficient as possible in terms of time and money, and scalable"

One of the hardest decisions we had to take in our design was the utilization of AWS instances. AWS is very cheap but not free,
and the more instances/queues/storage one uses, the less time it takes for all jobs to be completed. The whole challenge was to
weight the different tradeoffs and find the best implementation given the task at hand.

We decided to use the following configuration:

- [x] 4 SQS - Local &lrarr; Manager &lrarr; Workers,
- [x] 1 S3 Bucket (Storage Service),
- [x] 1 Manager Instance (EC2) and as many workers as necessary (300 PDFs/Worker)

## Scalability

### Jobs partition

To make the system as distributed as possible, we defined a job to be a single PDF file which requires processing. Like we mentioned [in the previous section](#experimentation-), 
Workers are created every 300 requests so that means every job will be executed as fast as possible.

### Workers

As we saw [above](#jobs-partition), the jobs consist of a PDF operation. In order to maximize task
completion rate, we decided to attribute 300 jobs to every worker. That means that if a task contains 500 PDFs to process, 2 workers 
will be created. However, what if millions of PDFs were sent? Will we create thousands of workers to deal with them?

An exemplary way to deal with this issue is making it possible for a worker to run several threads in parallel (process several PDFs
at the same time). Because a Worker's EC2 instance only runs one program during its lifetime, this shouldn't pose a serious problem, 
__given it has enough memory__. If memory becomes an issue it is possible to upgrade the Worker's type to `T2_LARGE` or even larger and run on them as
many threads as necessary.  
For example, each thread should process &plusmn;100 PDFs &rarr; if we choose to limit the number of workers to 20, in order to 
process 1M PDFs we would need each worker to run 250 threads. Is is realistic? Yes, but a very large type should be chosen 
so the program runs smoothly.  
Another optional design idea would be to increase the number of PDFs per thread &rarr; **Longer processing times (not a good idea)**.
>Note: a Java VM cannot support more than 256 threads.

### Manager
The main issue which hinders scalability is the manager. When we talked about [types](#type), we mentioned that two threads were running on the
Manager instance:
* Thread 1: Deals with task requests from the local application and with the creation of new workers.
* Thread 2: Creates a report for each task by collecting the response messages from the workers.

With only two threads it doesn't matter how many workers exist. The manager processes one response at the time,
so we lose all the advantages of running a lot of workers.  


### Simple Queue Service (SQS)
We used 4 queues:
1. Local &rarr; Manager
2. Manager &rarr; Local
3. Manager &rarr; Workers
4. Workers &rarr; Manager

This way, except for the Manager &rarr; Local queue, every time a message is received we can be certain it is received by the 
correct recipient.

## SQS Messages

### Local &rarr; Manager

* Attributes
  * `Name` : "New Task"
  * `Task` : unique `taskId` that will be used for the final report
  * `Type` : "Task"

* Body

```json
{
  "PDF-file-location": "location_of_the_file_with_the_PDFs_in_s3(.txt)",
  "task-id": "unique task id used for the final report",
  "N": "how many jobs to give each worker (Integer)",
  "terminate": "should the manager be terminated when the task is finished (true or false)"
}
```

### Manager &rarr; Local

#### Report ready
* Attributes
  * `Name` : "Task completed"
  * `Target` : unique `taskId` which was transmitted during task request
  * `Type` : "Report"

* Body

```json
{
  "report-file-location": "location_of_the_file_with_the_report_in_s3(.txt)",
  "task-id": "unique task id which was transmitted during task request"
}
```

#### Terminate
* Attributes
  * `Name` : "Terminated"
  * `Terminate` : unique `taskId` transmitted during task request (to target the correct sender)
  * `Type` : "Terminate"

* Body: "Terminate"
> This message is only sent once when all the Workers instances have terminated and all the LocalApplications that sent a
> task request before the `terminate` have completed and received their reports

### Manager &rarr; Worker(s)

* Attributes
  * `Name` : "New Job"
  * `Sender` : unique `taskId` __created by manager__
  * `Type` : "Job"

* Body

```json
{
  "original": "http://www.jewishfederations.org/local_includes/downloads/39497.pdf",
  "operation": "ToImage"
}
```
> The body is a single PDF link in JSON format to be processed by a worker

### Worker &rarr; Manager

* Attributes
  * `Name` : "Job completed:\t" + job_name + "\tFrom: " + sender
  * `Sender` : Same `Sender` attribute sent by the manager
  * `Type` : "Job Completed"

* Body

```json
{
  "original": "http://www.jewishfederations.org/local_includes/downloads/39497.pdf",
  "operation": "ToImage",
  "changed": "39497.png"
}
```
> The body is a single PDF details in JSON format to be added to the report of the `Sender`

## Issues encountered

* Because we use student account, we are limited to 19 instances. To prevent our account from being
  blocked, we limited the amount of workers to 10. If somehow a task requires processing more than 4000 PDFs,
  we won't be able to create more workers, and the amount of jobs per worker would grow as well as the task's
  completion time.


* If we assume that the number of EC2 instances we can create isn't an issue (we can create as many
  workers as we want), if a lot of tasks (millions) are sent to the manager (by millions of different local
  applications), the manager will be overwhelmed very fast.

  * One solution would be to add more queues:
      * We could for example create one queue and one thread per worker.
        
      * However, more threads on the manager side may lead to concurrency issues since the manager needs to store all the data it receives form the workers.

  * Perhaps we could add more managers? How do we keep the scalability of the system?

    * Firsr we add a layer of managers (in a tree) and randomly distribute the jobs to different sides of the tree

    * The main manager will then merge the reports of its two subordinates and send the result to the
      corresponding local app

    * That would mean 2<sup>layers</sup>-1 Managers' instances. Is that better ? Debatable.
  


* Another issue we found in the assignment is the jobs' repartition:
  ```txt
  "If there are k active workers, and the new job requires m workers, then the manager should create m-k new workers, if possible"
  ```  
  The problem is that if millions of tasks only require 2 workers (<600 jobs per task), these 2 workers will have to
  deal with the millions of jobs alone. For this reason we decided to keep a count of the `pending jobs`. When a new task is
  received by the manager, it will take into account the number of pending jobs and create more workers if
  necessary.  
  For instance, there are currently `2 workers` running, `450 jobs pending` and `N = 300`. A new task arrives
  with `900 new jobs`. In total, there are now `1350 jobs pending` for only 2 workers. Hence, `ceil(1350/300) - 2 = 3`
  more workers will be created.

## Mandatory Requirements

- Did you think for more than 2 minutes about security? 
> Yes. Credentials are not hard-coded. There are other security means possible to implement but we didn't manage to get to this task.
> For example: 
> Creating a security-group that allows SSH connection only to only our IP addresses.
> Storing the jar files on a random S3 (the first Local Application that starts the Manager upload the jar files compressed with a password, randomly defined during initialization and passed as user-data)

- Did you think about scalability?
> Yes. See [Scalability](#scalability)

- What about persistence? 
> The system can't finish if one PDF is still in process. If somehow a PDF takes too much time to complete (more than 10 minutes), another worker will be able to perform it.

- What if a node dies?
> Every worker VM has at all times 3 threads running. If a thread dies (unexpected error), another thread will be started. If the EC2 instance fails, the manager should be able to detect it (checking the status of each worker) and start another EC2 instance accordingly.

 - What if a node stalls for a while? 
 > We didn't find a way to deal with this problem nor did we encounter it. For a node to stall, all 3 threads must stall, which didn't happen

- What about broken communications? 
> The manager itself sends the information to connect to the queues. If a worker is disconnected from a queue, an error will be thrown, the thread terminated and another thread will be started at its place, reconnecting to the correct instances (redownloading the file containing the queues information from S3).

 - Threads in your application, when is it a good idea? When is it bad? Invest time to think about threads in your application!
 > See [Scalability](#scalability)

 - Did you run more than one client at the same time?
 > Yes. You can see the screen capture in the `Output_Files` folder.

- Did you manage the termination process?
> Yes. The manager deletes the workers and their queues. The local that requested the termination deletes the Manager and the queues. At the end only the two `jar` files (Manager.jar and Worker.jar), and the console of the manager as well as the output of the PDF operations should be on the S3 bucket. (We could delete the S3 bucket during the termination but that would mean every time uploading the jars to the bucket which takes some time).

- Did you take in mind the system limitations that we are using?
> Yes. We limited the number of instances running at the same time and [more](#issues-encountered).

- Are all your workers working hard? Or some are slacking? Why?
> Yes. It is possible sometimes that a specific thread of a worker is blocked on a large PDF and blocks the process but there are two more threads that works.

- Is your manager doing more work than he's supposed to? Have you made sure each part of your system has properly defined tasks? Did you mix their tasks?
> Every one is doing its job. The local application sends the task and creates an html file with the report. The Manager receives tasks from local apps and sends jobs to the Workers, and adds the responses from the workers to the corresponding report and when all the jobs have been completed, sends the final report to the corresponding local app. Finaly, the workers only process PDFs and send responses to the manager.

- Are you sure you understand what distributed means? Is there anything in your system awaiting another?
> Except for waiting the others to finish their jobs (manager waits for workers to send responses and local for manager to send the report...) all the system is distributed thanks to SQS. A worker doesn't know what the other workers do.

