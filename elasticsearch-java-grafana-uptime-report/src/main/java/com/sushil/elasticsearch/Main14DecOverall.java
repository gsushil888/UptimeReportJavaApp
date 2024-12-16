package com.sushil.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jakarta.json.stream.JsonParser;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.*;

import java.util.Properties;

public class Main14DecOverall {

	private static final String JSON_FILE_PATH_JFS = ConfigLoader.get("json.file.path.jfs");
	private static final String JSON_FILE_PATH_OVERALL_JFS = ConfigLoader.get("json.file.path.overall.jfs");

	private static final String JSON_FILE_PATH_WMC = ConfigLoader.get("json.file.path.wmc");
	private static final String JSON_FILE_PATH_OVERALL_WMC = ConfigLoader.get("json.file.path.overall.wmc");

	private static final String JSON_FILE_PATH_AMC = ConfigLoader.get("json.file.path.amc");
	private static final String JSON_FILE_PATH_OVERALL_AMC = ConfigLoader.get("json.file.path.overall.amc");

	private static final String PDF_FILE_PATH_JFS = ConfigLoader.get("pdf.file.path.jfs");
	private static final String PDF_FILE_PATH_WMC = ConfigLoader.get("pdf.file.path.wmc");
	private static final String PDF_FILE_PATH_AMC = ConfigLoader.get("pdf.file.path.amc");

	private static final String FROM_EMAIL = ConfigLoader.get("email.from");
	private static final List<String> RECIPIENTS = ConfigLoader.getList("email.recipients");
	private static final String EMAIL_HOST = ConfigLoader.get("email.host");
	private static final String EMAIL_USERNAME = ConfigLoader.get("email.username");
	private static final String EMAIL_PASSWORD = ConfigLoader.get("email.password");
	private static Logger logger = LoggerFactory.getLogger(Main13DecOverall.class);
	private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private static String REPORT_FROM = "";
	private static String REPORT_TO = "";

	public static void main(String[] args) {

		// Generate current date in dd_MM_yyyy_EEE format to pdf file name
		String formattedDate = new SimpleDateFormat("dd_MM_yyyy_EEE").format(new Date());

		// Generate PDF paths with date
		String pdfPathJFS = PDF_FILE_PATH_JFS.replace(".pdf", "_" + formattedDate + ".pdf");
		String pdfPathWMC = PDF_FILE_PATH_WMC.replace(".pdf", "_" + formattedDate + ".pdf");
		String pdfPathAMC = PDF_FILE_PATH_AMC.replace(".pdf", "_" + formattedDate + ".pdf");

		List<String> pdfFilePaths = new ArrayList<>();
		pdfFilePaths.add(generatePdfReport(pdfPathJFS,"JFS", JSON_FILE_PATH_JFS, JSON_FILE_PATH_OVERALL_JFS));
		pdfFilePaths
				.add(generatePdfReport(pdfPathWMC,"BLACKROCK WMC", JSON_FILE_PATH_WMC, JSON_FILE_PATH_OVERALL_WMC));
		pdfFilePaths
				.add(generatePdfReport(pdfPathAMC,"BLACKROCK AMC", JSON_FILE_PATH_AMC, JSON_FILE_PATH_OVERALL_AMC));
		System.out.println(pdfFilePaths);
		// Send single email with multiple attachments
		String emailSubject = "Uptime Reports";
		String emailMessage = "Dear Team,\n\nPlease find the attached Uptime Reports for your reference.\n\n"
				+ "Report Period: \n" + REPORT_FROM + " to " + REPORT_TO + "\n\n"
				+ "Entities: \nJFS, BLACKROCK WMC, BLACKROCK AMC\n\n"
				+ "Let me know if you have any questions.\n\nBest regards,";

		sendEmailWithAttachments(RECIPIENTS, emailSubject, emailMessage, pdfFilePaths);

//		// Generate and send reports with pdf having date in file names
//		generateAndSendReport(pdfPathJFS, "JFS", JSON_FILE_PATH_JFS, JSON_FILE_PATH_OVERALL_JFS);
//		generateAndSendReport(pdfPathWMC, "BLACKROCK WMC", JSON_FILE_PATH_WMC, JSON_FILE_PATH_OVERALL_WMC);
//		generateAndSendReport(pdfPathAMC, "BLACKROCK AMC", JSON_FILE_PATH_AMC, JSON_FILE_PATH_OVERALL_AMC);
	}

	// -------------------DATA EXTRACTION AND REPORT GENERATION
	// METHOD-------------------

	private static void generateAndSendReport(String pdfFilePath, String reportName, String jsonFilePath,
			String overallJsonFilePath) {
		try (FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
			// Connect with elasticsearch
			ElasticsearchClient client = ElasticsearchClientFactory.createClient();

			// create pdf document
			Document document = new Document();
			PdfWriter.getInstance(document, fos);
			document.open();

			// Add title of pdf document
			addTitleToDocument(document, reportName);

			// Extract Data for pdf document from elasticsearch
			extractDataFromElasticSearch(client, document, jsonFilePath, overallJsonFilePath);

			// close pdf document
			document.close();
			System.out.println("PDF generated successfully: " + pdfFilePath);

			// Send Email
			String emailSubject = "Uptime Report - " + reportName;
			String emailMessage = "Dear Team,\n\nPlease find the attached Uptime Report (" + reportName
					+ ") for your reference.\n\nLet me know if you have any questions.\n\nBest regards,";
			sendEmailWithAttachment(RECIPIENTS, emailSubject, emailMessage, pdfFilePath);

			// close connection with elastic search
			client.close();

		} catch (IOException | DocumentException e) {
			logger.error(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	private static String generatePdfReport(String pdfFilePath, String reportName, String jsonFilePath,
			String overallJsonFilePath) {
		try (FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
			// Connect with elasticsearch
			ElasticsearchClient client = ElasticsearchClientFactory.createClient();

			// create pdf document
			Document document = new Document();
			PdfWriter.getInstance(document, fos);
			document.open();

			// Add title of pdf document
			addTitleToDocument(document, reportName);

			// Extract Data for pdf document from elasticsearch
			extractDataFromElasticSearch(client, document, jsonFilePath, overallJsonFilePath);

			// close pdf document
			document.close();
			System.out.println("PDF generated successfully: " + pdfFilePath);

			// close connection with elastic search
			client.close();
			return pdfFilePath;

		} catch (IOException | DocumentException e) {
			logger.error(e.getLocalizedMessage());
			e.printStackTrace();
			return null;
		}
	}

	private static void extractDataFromElasticSearch(ElasticsearchClient client, Document document, String jsonFilePath,
			String overallJsonFilePath) throws IOException, DocumentException {
		// Extract time interval from JSON
		InputStream queryJsonFile = new FileInputStream(jsonFilePath);
		addTimeIntervalToPdfFromJson(queryJsonFile, document);

		// Extract overall uptime average
		InputStream queryStream = new FileInputStream(overallJsonFilePath);
		extractAverageUptime(client, document, queryStream);

		// Extract all records
		queryJsonFile.close(); // Close the first stream after use
		InputStream queryJsonFileForRecords = new FileInputStream(jsonFilePath);
		extractAllRecords(client, document, queryJsonFileForRecords);
		queryJsonFileForRecords.close(); // Close the second stream after use
	}

	// Generalized method for extracting overall UPTIME average
	private static void extractAverageUptime(ElasticsearchClient client, Document document, InputStream queryStream)
			throws IOException, DocumentException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
//		logger.warn(searchResponse + " ");
		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();

		// Add Section Header
		addStyledSectionHeader(document, "Overall Uptime Average :");

		// Create Table with Better Formatting
		PdfPTable table = new PdfPTable(new float[] { 3, 2 }); // Columns: 3 for URL, 2 for Avg Uptime
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);

		table.addCell(createHeaderCell("URL"));
		table.addCell(createHeaderCell("Average Uptime"));

		for (StringTermsBucket bucket : buckets) {
			String url = bucket.key().stringValue();
			Double avgUptime = bucket.aggregations().get("avg_uptime").avg().value();

			table.addCell(new Paragraph(url));

			// Ensure the uptime value is centered and includes "%" symbol
			PdfPCell uptimeCell = new PdfPCell(new Paragraph(String.format("%.2f%%", avgUptime)));
			uptimeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
			uptimeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

			// Check if the uptime percentage is less than 100 and highlight the cell
			if (avgUptime < 100) {
				uptimeCell.setBackgroundColor(BaseColor.CYAN); // Highlight with yellow if less than 100%
			}

			table.addCell(uptimeCell);
		}

		document.add(table);
	}

	// Generalized method for extracting all UPTIME records
	private static void extractAllRecords(ElasticsearchClient client, Document document, InputStream queryJsonFile)
			throws IOException, DocumentException {
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryJsonFile);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

//		logger.warn(searchResponse + " ");

		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();

		// Section 1: URLs with <100% uptime
		addStyledSectionHeader(document, "Section 1: Time at which url uptime is less than 100%");
		for (StringTermsBucket bucket : buckets) {
			String url = bucket.key().stringValue();
			Aggregate avgUptimeAggregations = bucket.aggregations().get("hourly_avg");
			List<DateHistogramBucket> avgUptimeBuckets = avgUptimeAggregations.dateHistogram().buckets().array();

			// Filter for records <100%
			List<DateHistogramBucket> lessThan100Buckets = new ArrayList<>();
			for (DateHistogramBucket datebucket : avgUptimeBuckets) {
				double uptimeValue = datebucket.aggregations().get("avg_uptime").avg().value();
				if (uptimeValue < 100.0) {
					lessThan100Buckets.add(datebucket);
				}
			}

			// Add URL header as part of the table
			PdfPTable table = createTableWithUrlHeader(url);
			if (lessThan100Buckets.isEmpty()) {
				addNoDowntimeRow(table);
			} else {
				populateTableWithData(table, lessThan100Buckets);
			}
			document.add(table);
		}

		// Section 2: All Records
		addStyledSectionHeader(document, "Section 2: All records between the specified range");
		for (StringTermsBucket bucket : buckets) {
			String url = bucket.key().stringValue();
			Aggregate avgUptimeAggregations = bucket.aggregations().get("hourly_avg");
			List<DateHistogramBucket> allBuckets = avgUptimeAggregations.dateHistogram().buckets().array();

			// Add URL header as part of the table
			PdfPTable table = createTableWithUrlHeader(url);
			populateTableWithData(table, allBuckets);
			document.add(table);
		}
	}

	// ------------------------TABLE AND DOCUMENT METHODS--------------------------------------------------

	// Adding Time Interval from JSON to PDF Document Top
	public static void addTimeIntervalToPdfFromJson(InputStream queryJsonStream, Document document) {
		try {
			// Parse the JSON query from InputStream
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);

			// Extract the necessary data
			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText(); // Greater than or equal to date
			String reportToTimestamp = rangeNode.path("lt").asText(); // Less than or equal to date
			String timeZoneTimestamp = rangeNode.path("time_zone").asText(); // Less than or equal to date

			// Add time interval section to the document
			try {
				if (!reportFromTimestamp.equals("now-1d/d")) {
					REPORT_FROM = parseDate(reportFromTimestamp);
				}
				if (!reportToTimestamp.equals("now-1d/d")) {
					REPORT_TO = parseDate(reportFromTimestamp);
				}
				addStyledSectionHeader(document,
						"From: " + parseDate(reportFromTimestamp) + " |  To: " + parseDate(reportToTimestamp));
			} catch (DocumentException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String parseDate(String dateInput) {
		if (dateInput.contains("now")) {
			LocalDate today = LocalDate.now();
			System.out.println("From formatted "
					+ formatDate(today.minusDays(1).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT)));
			System.out.println("To formatted "
					+ formatDate(today.minusDays(0).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT)));

			if (dateInput.equals("now-1d/d")) {
				REPORT_FROM = formatDate(
						today.minusDays(1).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				return formatDate(today.minusDays(1).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
			} else if (dateInput.equals("now/d")) {
				REPORT_TO = formatDate(
						today.minusDays(0).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
				return formatDate(today.minusDays(0).atStartOfDay().minusHours(5).minusMinutes(30).format(ISO_FORMAT));
			}
		} else {
			// Assume absolute ISO date
			return formatDate(dateInput);
		}
		return "";
	}

	// Adding document title to PDF Top
	public static void addTitleToDocument(Document document, String reportName) throws DocumentException {
		// Format the current date and time
		SimpleDateFormat sdf = new SimpleDateFormat("EEEE dd-MMM-yyyy hh:mm:ss a");
		String currentDateTime = sdf.format(new Date());

		// Define the font for the title
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);

		// Create the title paragraph
		Paragraph title = new Paragraph(
				"Uptime Percentage Report (" + reportName + ")\nGenerated At: " + currentDateTime + " IST",
				sectionFont);
		title.setAlignment(Element.ALIGN_CENTER);
		title.setSpacingAfter(20f);

		// Add the title to the document
		document.add(title);
	}

	// Creates table with a URL header as the first row
	private static PdfPTable createTableWithUrlHeader(String url) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
		table.setWidths(new float[] { 3f, 2f });

		// Add URL header in the first row of the table
		PdfPCell urlHeaderCell = new PdfPCell(
				new Paragraph("URL: " + url, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
		urlHeaderCell.setColspan(2);
		urlHeaderCell.setBackgroundColor(BaseColor.YELLOW);
		urlHeaderCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		urlHeaderCell.setPadding(5f);
		table.addCell(urlHeaderCell);

		// Add Table Headers for Timestamp and Uptime Percentage
		table.addCell(createHeaderCell("Timestamp"));
		table.addCell(createHeaderCell("Uptime Percentage"));

		return table;
	}

	// Helper Method: Styled Section Header
	private static void addStyledSectionHeader(Document document, String title) throws DocumentException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
		Paragraph sectionHeader = new Paragraph(title, sectionFont);
		sectionHeader.setSpacingBefore(5f);
		sectionHeader.setSpacingAfter(5f);
		sectionHeader.setAlignment(Element.ALIGN_LEFT);
		document.add(sectionHeader);
	}

	// Adding No Down time row if no records found below 100%
	private static void addNoDowntimeRow(PdfPTable table) {
		PdfPCell noDowntimeCell = new PdfPCell(new Paragraph("No Downtime"));
		noDowntimeCell.setColspan(2);
		noDowntimeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		table.addCell(noDowntimeCell);
	}

	// Populates table with data
	private static void populateTableWithData(PdfPTable table, List<DateHistogramBucket> buckets) {
		for (DateHistogramBucket bucket : buckets) {
			double uptimeValue = bucket.aggregations().get("avg_uptime").avg().value();
			String timestamp = formatDate(bucket.keyAsString());
			// Format uptime as a percentage
			String uptimePercentage = String.format("%.2f%%", uptimeValue);

			table.addCell(new Paragraph(timestamp));
			PdfPCell uptimeCell = new PdfPCell(new Paragraph(uptimePercentage));
			uptimeCell.setHorizontalAlignment(Element.ALIGN_CENTER); // Center alignment
			uptimeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

			// Highlight cells with uptime < 100%
			if (uptimeValue < 100) {
				uptimeCell.setBackgroundColor(BaseColor.CYAN); // Highlight with cyan
			}

			table.addCell(uptimeCell);
		}
	}

	// Utility method for header cell creation
	private static PdfPCell createHeaderCell(String text) {
		PdfPCell cell = new PdfPCell(new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
		cell.setPadding(5f);
		return cell;
	}

	// Formats date strings
	public static String formatDate(String dateString) {
		Instant instant = Instant.parse(dateString);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")
				.withZone(ZoneId.of("Asia/Kolkata"));
		return formatter.format(instant);
	}

	// Send email with attachment
	public static void sendEmailWithAttachment(List<String> toRecipients, String subject, String body,
			String filePath) {

		Properties properties = System.getProperties();
		properties.put("mail.smtp.host", EMAIL_HOST);
		properties.put("mail.smtp.port", "587");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.ssl.enable.enable", "true");
		properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

		Session session = Session.getInstance(properties, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
			}
		});

		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(FROM_EMAIL));
			// Add multiple recipients
			for (String recipient : toRecipients) {
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			}
			message.setSubject(subject);

			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(body);

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			// Attach the PDF file
			messageBodyPart = new MimeBodyPart();
			DataSource source = new FileDataSource(filePath);
			messageBodyPart.setDataHandler(new DataHandler(source));
			messageBodyPart.setFileName(filePath);
			multipart.addBodyPart(messageBodyPart);

			message.setContent(multipart);

			Transport.send(message);
			System.out.println("Email sent successfully to : " + RECIPIENTS);
		} catch (MessagingException mex) {
			System.err.println("Mail sending failure");
			mex.printStackTrace();
		}
	}

	// Send email with attachments
	public static void sendEmailWithAttachments(List<String> toRecipients, String subject, String body,
			List<String> filePaths) {

		Properties properties = System.getProperties();
		properties.put("mail.smtp.host", EMAIL_HOST);
		properties.put("mail.smtp.port", "587");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.ssl.enable.enable", "true");
		properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

		Session session = Session.getInstance(properties, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
			}
		});

		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(FROM_EMAIL));
			// Add multiple recipients
			for (String recipient : toRecipients) {
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			}
			message.setSubject(subject);

			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(body);

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			// Attach multiple files
			for (String filePath : filePaths) {
				if (filePath != null) {
					MimeBodyPart attachmentPart = new MimeBodyPart();
					DataSource source = new FileDataSource(filePath);
					attachmentPart.setDataHandler(new DataHandler(source));
					attachmentPart.setFileName(new File(filePath).getName());
					multipart.addBodyPart(attachmentPart);
				}
			}

			message.setContent(multipart);

			Transport.send(message);
			System.out.println("Email sent successfully to : " + RECIPIENTS);
		} catch (MessagingException mex) {
			System.err.println("Mail sending failure");
			mex.printStackTrace();
		}
	}

}
