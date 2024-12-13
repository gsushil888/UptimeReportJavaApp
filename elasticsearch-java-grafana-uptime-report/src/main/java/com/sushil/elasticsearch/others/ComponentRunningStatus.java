package com.sushil.elasticsearch.others;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.*;
import java.util.Base64;

public class ComponentRunningStatus {

	// Method to check if a service is up or down
	private static boolean isServiceUp(String url) {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet request = new HttpGet(url);
			// If Basic Authentication is needed
			request.setHeader("Authorization",
					"Basic " + Base64.getEncoder().encodeToString("elastic:Clover@123".getBytes()));

			try (CloseableHttpResponse response = httpClient.execute(request)) {
				int statusCode = response.getStatusLine().getStatusCode();
				return statusCode >= 200 && statusCode < 300; // Service is up if status code is 2xx
			}
		} catch (Exception e) {
			return false; // Service is down if an exception occurs
		}
	}

	// Method to send an email alert with the real-time status of all services
	private static void sendEmailAlert(String alertMessage) {
		// SMTP server configuration
		String host = "email.cloverinfotech.com"; // SMTP server
		final String from = "jflalert.monitoring@cloverinfotech.com"; // Sender's email
		final String appPassword = "Clover#2024"; // Sender's email password

		// Recipient's email
		String to = "suraj.vikhe@cloverinfotech.com"; // Replace with the recipient's email address

		// Email subject and body
		String subject = "Real-Time ELKG Status Alert";
		String body = alertMessage;

		// SMTP properties
		Properties properties = new Properties();
		properties.put("mail.smtp.host", host);
		properties.put("mail.smtp.port", "587"); // Port for STARTTLS
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true"); // Enable STARTTLS

		// Create a session with an authenticator
		Session session = Session.getInstance(properties, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(from, appPassword);
			}
		});

		try {
			// Create a message
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
			message.setSubject(subject);
			message.setText(body);

			// Send the email
			Transport.send(message);
			System.out.println("Email sent successfully.");
		} catch (MessagingException e) {
			e.printStackTrace();
			System.out.println("Failed to send email. Error: " + e.getMessage());
		}
	}

	// Main method to check multiple services and send status
	public static void main(String[] args) {
		// Define service URLs and their corresponding names
		String[] services = { "Elasticsearch|http://10.100.0.199:9200", "Logstash|http://10.100.0.199:9600",
				"Kibana|http://10.100.0.199:5601", "Grafana|http://10.100.0.199:3000", };

		StringBuilder statusReport = new StringBuilder("Real-Time ELKG Status:\n");
		boolean isAnyServiceDown = false; // Flag to track if any service is down

		// Check each service's status
		for (String service : services) {
			String[] serviceInfo = service.split("\\|");
			String serviceName = serviceInfo[0];
			String serviceUrl = serviceInfo[1];

			// Check if the service is up
			if (isServiceUp(serviceUrl)) {
				statusReport.append(serviceName).append(" at ").append(serviceUrl).append(" is UP!\n");
			} else {
				statusReport.append(serviceName).append(" at ").append(serviceUrl).append(" is DOWN!\n");
				isAnyServiceDown = true; // Set the flag to true if any service is down
			}
		}

		// Send email only if any service is down
		if (isAnyServiceDown) {
			sendEmailAlert(statusReport.toString());
		} else {
			System.out.println("All services are up. No email sent.");
		}

		// Print the status report to the console
		System.out.println(statusReport);
	}
}