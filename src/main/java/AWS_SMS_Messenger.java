import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsResult;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.OutputLogEvent;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

import org.json.JSONObject;

public final class AWS_SMS_Messenger{

    //Initial Variable Declaration
    private static final String AccessKey = "Put_Access_Key_Here";
    private static final String SecretAccessKey = "Put_Secret_Access_Key_Here";
    private static final Regions awsRegion = Regions.US_EAST_1; 
    private static final String phoneNum_countryCode = "+1"; //+1 applies to US Messages
    private static final String phoneNum = "Put_Phone_Number_Here"; //10 digit phone number ex. "2223331234"
    private static final String logGroupName = "Put_Log_Group_Name_Here";
    private static final BasicAWSCredentials basicAwsCredentials = new BasicAWSCredentials(AccessKey, SecretAccessKey);

    public static void main(String[] args) {
        AmazonSNS snsClient = AmazonSNSClient
                      .builder()
                      .withRegion(awsRegion)
                      .withCredentials(new AWSStaticCredentialsProvider(basicAwsCredentials))
                      .build();
        //Message can only contain contain 140 bytes maximum
        //Try With Emoji: 😀😁😍😎
        String message = "😎 Hello, World. 😎";
        String phoneNumber = phoneNum_countryCode + phoneNum;
        Map<String, MessageAttributeValue> smsAttributes = new HashMap<String, MessageAttributeValue>();
        //Maximum Price you're willing to spend, AWS will not send if the cost would be higher than this.
        smsAttributes.put("AWS.SNS.SMS.MaxPrice", new MessageAttributeValue()
                .withStringValue("0.06") //Must be non-zero, can be up to 5 decimals places
                .withDataType("Number"));
        smsAttributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
            .withStringValue("Promotional") //Either Promotional or Transactional
            .withDataType("String"));
        /*  Unneccessary Code, the US does not support SenderID

            Full Country List Here: https://docs.aws.amazon.com/pinpoint/latest/userguide/channels-sms-countries.html
        smsAttributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                .withStringValue("GoodMorning")
               .withDataType("String"));
        <set SMS attributes> */
        sendSMSMessage(snsClient, message, phoneNumber, smsAttributes);

        //The CloudWatch updates slow from my testing. 
        try {
            int sleepTime = 10; //10 minute wait before checking last logged SMSMessage
            for(int i = sleepTime; i > 0; i--){
                System.out.println(i + " minutes remaining until CloudWatch check.");
                TimeUnit.MINUTES.sleep(1);
            }
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        getLastSMSMessageLog(logGroupName);
    }

    public static void sendSMSMessage(AmazonSNS snsClient, String message,
		String phoneNumber, Map<String, MessageAttributeValue> smsAttributes) {
        PublishResult result = snsClient.publish(new PublishRequest()
                        .withMessage(message)
                        .withPhoneNumber(phoneNumber)
                        .withMessageAttributes(smsAttributes));
        System.out.println("Publish Message ID: " + result.getMessageId());
    }

    public static void getLastSMSMessageLog(String logGroupName){
        ClientConfiguration clientConfig = new ClientConfiguration();
        AWSLogs logsClient = AWSLogsClient
                    .builder()
                    .withCredentials(new AWSStaticCredentialsProvider(basicAwsCredentials))
                    .withRegion(awsRegion)
                    .withClientConfiguration(clientConfig)
                    .build();
            
            //Get the latest log in the CloudWatch Logs, from the specific Log Group using logGroupName variable
            DescribeLogStreamsRequest describeLogStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(logGroupName).withDescending(true).withOrderBy("LastEventTime");
            DescribeLogStreamsResult describeLogStreamsResult = logsClient.describeLogStreams( describeLogStreamsRequest);
            GetLogEventsRequest getLogEventsRequest = new GetLogEventsRequest()
                        .withLogGroupName(logGroupName)
                        .withLogStreamName(describeLogStreamsResult.getLogStreams().get(0).getLogStreamName());

            //Takes String as JSON format and parses using org.json
            OutputLogEvent log = logsClient.getLogEvents(getLogEventsRequest).getEvents().get(0);
            if(log == null){
                return;
            }
            String json = log.getMessage();
            JSONObject logObject = new JSONObject(json);
            String log_messageId = logObject.getJSONObject("notification").getString("messageId");
            String phoneCarrier = logObject.getJSONObject("delivery").getString("phoneCarrier");
            Double priceInUSD = logObject.getJSONObject("delivery").getDouble("priceInUSD");
            String providerResponse = logObject.getJSONObject("delivery").getString("providerResponse");

            //Prints relavent information regarding the log
            System.out.println("Log Message ID: " + log_messageId);
            System.out.println("\tPhone Carrier: " + phoneCarrier);
            System.out.println("\tPrice in USD: " + priceInUSD);
            System.out.println("\tProvider Response: " + providerResponse);
    }
}