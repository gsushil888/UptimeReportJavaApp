package com.sushil.elasticsearch.old_backups;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.sushil.elasticsearch.ConfigLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
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

public class Main11DecOld {

	private static final String JSON_FILE_PATH = ConfigLoader.get("json.file.path");
	private static final String JSON_FILE_PATH_OVERALL = ConfigLoader.get("json.file.path.overall");
	private static final String PDF_FILE_PATH = ConfigLoader.get("pdf.file.path");

	private static final String ELASTIC_USERNAME = ConfigLoader.get("elastic.username");
	private static final String ELASTIC_PASSWORD = ConfigLoader.get("elastic.password");
	private static final String ELASTIC_HOST = ConfigLoader.get("elastic.host");
	private static final int ELASTIC_PORT = ConfigLoader.getInt("elastic.port");

	private static final String FROM_EMAIL = ConfigLoader.get("email.from");
	private static final List<String> RECIPIENTS = ConfigLoader.getList("email.recipients");
	private static final String EMAIL_HOST = ConfigLoader.get("email.host");
	private static final String EMAIL_USERNAME = ConfigLoader.get("email.username");
	private static final String EMAIL_PASSWORD = ConfigLoader.get("email.password");

	public static void main(String[] args) {
		BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
		credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(ELASTIC_USERNAME, ELASTIC_PASSWORD));
		RestClientBuilder builder = RestClient.builder(new HttpHost(ELASTIC_HOST, ELASTIC_PORT, "http"))
				.setHttpClientConfigCallback(
						httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProv));

		try (RestClient restClient = builder.build(); FileOutputStream fos = new FileOutputStream(PDF_FILE_PATH)) {
			ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
			ElasticsearchClient client = new ElasticsearchClient(transport);

			// Open a single PDF document
			Document document = new Document();
			PdfWriter.getInstance(document, fos);
			document.open();

			// Add title
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String currentDateTime = sdf.format(new Date());
			Paragraph title = new Paragraph("Uptime Percentage Report\nDate - " + currentDateTime + " IST");
			title.setAlignment(Element.ALIGN_CENTER);
			title.setSpacingAfter(20f);
			document.add(title);

			// Section 1: Overall Data
//			document.add(new Paragraph("Overall Uptime Averages:"));
			executeSearchQueryOverall(client, document);

			// Section 2: Detailed Data
//			document.add(new Paragraph("Detailed Hourly Uptime Data:"));
			executeSearchQuery(client, document);

			// Close document
			document.close();
			System.out.println("PDF generated successfully!");
			sendEmailWithAttachment(RECIPIENTS, "Generated Report", "Please find the report attached.", PDF_FILE_PATH);

		} catch (IOException | DocumentException e) {
			e.printStackTrace();
		}
	}

	public static void executeSearchQuery(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH);
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();

		// Section 1: URLs with <100% uptime
		addStyledSectionHeader(document, "Section 1: URLs with Uptime < 100%");
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

			// Add URL header
			addUrlHeader(document, url);

			PdfPTable table = createTable();
			if (lessThan100Buckets.isEmpty()) {
				addNoDowntimeRow(table);
			} else {
				populateTableWithData(table, lessThan100Buckets);
			}
			document.add(table);
		}

		// Section 2: All Records
		addStyledSectionHeader(document, "Section 2: All Records for All URLs");
		for (StringTermsBucket bucket : buckets) {
			String url = bucket.key().stringValue();
			Aggregate avgUptimeAggregations = bucket.aggregations().get("hourly_avg");
			List<DateHistogramBucket> allBuckets = avgUptimeAggregations.dateHistogram().buckets().array();

			// Add URL header
			addUrlHeader(document, url);

			PdfPTable table = createTable();
			populateTableWithData(table, allBuckets);
			document.add(table);
		}
	}

	public static void executeSearchQueryOverall(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_OVERALL);
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

		Map<String, Aggregate> aggregate = searchResponse.aggregations();
		Aggregate groupByUrlAggregation = aggregate.get("group_by_url");
		List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();

		// Add Section Header
		addStyledSectionHeader(document, "Last Day Overall Uptime Average for All URLs:");

		// Create Table with Better Formatting
		PdfPTable table = new PdfPTable(new float[] { 3, 2 }); // Columns: 3 for URL, 2 for Avg Uptime
		table.setWidthPercentage(100);
		table.setSpacingBefore(10f);
		table.setSpacingAfter(10f);

		table.addCell(createHeaderCell("URL"));
		table.addCell(createHeaderCell("Average Uptime"));

		for (StringTermsBucket bucket : buckets) {
			String url = bucket.key().stringValue();
			Double avgUptime = bucket.aggregations().get("avg_uptime").avg().value();

			table.addCell(new Paragraph(url));
			table.addCell(new Paragraph(String.format("%.2f%%", avgUptime)));
		}

		document.add(table);
	}

// Helper Method: Styled Section Header
	private static void addStyledSectionHeader(Document document, String title) throws DocumentException {
		Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
		Paragraph sectionHeader = new Paragraph(title, sectionFont);
		sectionHeader.setSpacingBefore(20f);
		sectionHeader.setSpacingAfter(10f);
		sectionHeader.setAlignment(Element.ALIGN_LEFT);
		document.add(sectionHeader);
	}

// Adds URL Header with yellow background
	private static void addUrlHeader(Document document, String url) throws DocumentException {
		PdfPTable headerTable = new PdfPTable(1);
		headerTable.setWidthPercentage(100);
		PdfPCell headerCell = new PdfPCell(
				new Paragraph("URL: " + url, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
		headerCell.setBackgroundColor(BaseColor.YELLOW);
		headerCell.setPadding(5f);
		headerCell.setHorizontalAlignment(Element.ALIGN_LEFT);
		headerTable.addCell(headerCell);
		document.add(headerTable);
	}

// Creates table with headers
	private static PdfPTable createTable() throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setSpacingBefore(10f);
		table.setSpacingAfter(10f);
		table.setWidths(new float[] { 3f, 2f });

		table.addCell(createHeaderCell("Timestamp"));
		table.addCell(createHeaderCell("Uptime Percentage"));
		return table;
	}

// Adds "No Downtime" row
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
			String uptimePercentage = String.format("%.2f", uptimeValue);

			table.addCell(new Paragraph(timestamp));
			PdfPCell uptimeCell = new PdfPCell(new Paragraph(uptimePercentage));
			uptimeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
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

}
