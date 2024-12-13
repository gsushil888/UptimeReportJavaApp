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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class Main12DecOverall {

	private static final String JSON_FILE_PATH = ConfigLoader.get("json.file.path");
	private static final String JSON_FILE_PATH_OVERALL = ConfigLoader.get("json.file.path.overall");

	private static final String JSON_FILE_PATH_WMC = ConfigLoader.get("json.file.path.wmc");
	private static final String JSON_FILE_PATH_OVERALL_WMC = ConfigLoader.get("json.file.path.overall.wmc");

	private static final String JSON_FILE_PATH_AMC = ConfigLoader.get("json.file.path.amc");
	private static final String JSON_FILE_PATH_OVERALL_AMC = ConfigLoader.get("json.file.path.overall.amc");

	private static final String PDF_FILE_PATH = ConfigLoader.get("pdf.file.path");
	private static final String PDF_FILE_PATH_WMC = ConfigLoader.get("pdf.file.path.wmc");
	private static final String PDF_FILE_PATH_AMC = ConfigLoader.get("pdf.file.path.amc");

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
		generateAndSendReport(PDF_FILE_PATH, "JFS", Main12DecOverall::extractOverallAverageUptimeJFS,
				Main12DecOverall::extractAllUptimeRecordsJFS);
		generateAndSendReport(PDF_FILE_PATH_WMC, "BLACKROCK WMC", Main12DecOverall::extractOverallAverageUptimeWMC,
				Main12DecOverall::extractAllUptimeRecordsWMC);
		generateAndSendReport(PDF_FILE_PATH_AMC, "BLACKROCK AMC", Main12DecOverall::extractOverallAverageUptimeAMC,
				Main12DecOverall::extractAllUptimeRecordsAMC);
	}

	private static void generateAndSendReport(String pdfFilePath, String reportName,
			ReportDataExtractor overallExtractor, ReportDataExtractor allRecordsExtractor) {
		BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
		credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(ELASTIC_USERNAME, ELASTIC_PASSWORD));

		RestClientBuilder builder = RestClient.builder(new HttpHost(ELASTIC_HOST, ELASTIC_PORT, "http"))
				.setHttpClientConfigCallback(
						httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProv));

		try (RestClient restClient = builder.build(); FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
			ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
			ElasticsearchClient client = new ElasticsearchClient(transport);

			Document document = new Document();
			PdfWriter.getInstance(document, fos);
			document.open();

			// Add Title
			SimpleDateFormat sdf = new SimpleDateFormat("EEEE dd-MMM-yyyy hh:mm:ss a");
			String currentDateTime = sdf.format(new Date());
			Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
			Paragraph title = new Paragraph(
					"Uptime Percentage Report (" + reportName + ")\nGenerated At: " + currentDateTime + " IST",
					sectionFont);
			title.setAlignment(Element.ALIGN_CENTER);
			title.setSpacingAfter(20f);
			document.add(title);

			// Extract Data
			overallExtractor.extract(client, document);
			allRecordsExtractor.extract(client, document);

			document.close();
			System.out.println("PDF generated successfully: " + pdfFilePath);

			// Send Email
			String emailSubject = "Uptime Report - " + reportName;
			String emailMessage = "Dear Team,\n\nPlease find the attached Uptime Report (" + reportName
					+ ") for your reference.\n\nLet me know if you have any questions.\n\nBest regards,";
			sendEmailWithAttachment(RECIPIENTS, emailSubject, emailMessage, pdfFilePath);
		} catch (IOException | DocumentException e) {
			e.printStackTrace();
		}
	}

	@FunctionalInterface
	interface ReportDataExtractor {
		void extract(ElasticsearchClient client, Document document) throws DocumentException, IOException;
	}

	// Overall Avg Uptime For Time Range
	public static void extractOverallAverageUptimeJFS(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_OVERALL);
		InputStream queryJsonFile = new FileInputStream(JSON_FILE_PATH);
		extractTimeIntervalFromJsonJFS(queryJsonFile, document);
//		// Now, reset the stream to the beginning before reusing it
//		queryStream.close(); // Close the stream after extracting the data
//		queryStream = new FileInputStream(JSON_FILE_PATH); // Reopen the InputStream

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

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

	// All Records
	public static void extractAllUptimeRecordsJFS(ElasticsearchClient client, Document document)
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

	// Extract from and to from json
	public static void extractTimeIntervalFromJsonJFS(InputStream queryJsonStream, Document document) {
		try {
			// Parse the JSON query from InputStream
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);

			// Extract the necessary data
			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText(); // Greater than or equal to date
			String reportToTimestamp = rangeNode.path("lt").asText(); // Less than or equal to date

			JsonNode categoryNode = rootNode.path("query").path("bool").path("filter").get(1).path("term")
					.path("category.keyword");
			String category = categoryNode.asText(); // Category data

			JsonNode aggNode = rootNode.path("aggs").path("group_by_url").path("aggs").path("hourly_avg")
					.path("date_histogram");
			String fixedInterval = aggNode.path("fixed_interval").asText(); // Fixed interval

			// Format the dates
			String formattedGte = formatDate(reportFromTimestamp);
			String formattedLte = formatDate(reportToTimestamp);

			addStyledSectionHeader(document, String.format("From: %s | To: %s ", formattedGte, formattedLte));

			// Generate and print the report
			System.out.println("Report Date and Time Range: " + formattedGte + " to " + formattedLte);
			System.out.println("Category: " + category);
			System.out.println("Fixed Interval: " + fixedInterval);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ----------------WMC--------------------------------
	// Overall Avg Uptime For Time Range
	public static void extractOverallAverageUptimeWMC(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_OVERALL_WMC);
		InputStream queryJsonFile = new FileInputStream(JSON_FILE_PATH_WMC);
		extractTimeIntervalFromJsonJFS(queryJsonFile, document);

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

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

	// All Records
	public static void extractAllUptimeRecordsWMC(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_WMC);

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

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

	// From and to
	public static void extractTimeIntervalFromJsonWMC(InputStream queryJsonStream, Document document) {
		try {
			// Parse the JSON query from InputStream
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);

			// Extract the necessary data
			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText(); // Greater than or equal to date
			String reportToTimestamp = rangeNode.path("lt").asText(); // Less than or equal to date

			JsonNode categoryNode = rootNode.path("query").path("bool").path("filter").get(1).path("term")
					.path("category.keyword");
			String category = categoryNode.asText(); // Category data

			JsonNode aggNode = rootNode.path("aggs").path("group_by_url").path("aggs").path("hourly_avg")
					.path("date_histogram");
			String fixedInterval = aggNode.path("fixed_interval").asText(); // Fixed interval

			// Format the dates
			String formattedGte = formatDate(reportFromTimestamp);
			String formattedLte = formatDate(reportToTimestamp);

			addStyledSectionHeader(document, String.format("From: %s | To: %s ", formattedGte, formattedLte));

			// Generate and print the report
			System.out.println("Report Date and Time Range: " + formattedGte + " to " + formattedLte);
			System.out.println("Category: " + category);
			System.out.println("Fixed Interval: " + fixedInterval);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// --------------AMC----------------------------------
	// Overall Avg Uptime For Time Range
	public static void extractOverallAverageUptimeAMC(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_OVERALL_AMC);
		InputStream queryJsonFile = new FileInputStream(JSON_FILE_PATH_AMC);
		extractTimeIntervalFromJsonJFS(queryJsonFile, document);

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

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

	// All Records
	public static void extractAllUptimeRecordsAMC(ElasticsearchClient client, Document document)
			throws IOException, DocumentException {
		InputStream queryStream = new FileInputStream(JSON_FILE_PATH_AMC);

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser jsonParser = jsonpMapper.jsonProvider().createParser(queryStream);

		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(jsonParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

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

	// From and to
	public static void extractTimeIntervalFromJsonAMC(InputStream queryJsonStream, Document document) {
		try {
			// Parse the JSON query from InputStream
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(queryJsonStream);

			// Extract the necessary data
			JsonNode rangeNode = rootNode.path("query").path("bool").path("filter").get(0).path("range")
					.path("@timestamp");
			String reportFromTimestamp = rangeNode.path("gte").asText(); // Greater than or equal to date
			String reportToTimestamp = rangeNode.path("lt").asText(); // Less than or equal to date

			JsonNode categoryNode = rootNode.path("query").path("bool").path("filter").get(1).path("term")
					.path("category.keyword");
			String category = categoryNode.asText(); // Category data

			JsonNode aggNode = rootNode.path("aggs").path("group_by_url").path("aggs").path("hourly_avg")
					.path("date_histogram");
			String fixedInterval = aggNode.path("fixed_interval").asText(); // Fixed interval

			// Format the dates
			String formattedGte = formatDate(reportFromTimestamp);
			String formattedLte = formatDate(reportToTimestamp);

			addStyledSectionHeader(document, String.format("From: %s | To: %s ", formattedGte, formattedLte));

			// Generate and print the report
			System.out.println("Report Date and Time Range: " + formattedGte + " to " + formattedLte);
			System.out.println("Category: " + category);
			System.out.println("Fixed Interval: " + fixedInterval);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// --------------------------------------------------------------------------

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

	private static void addStyledParagraph(Document document, String text) throws DocumentException {
		Font paragraphFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, BaseColor.BLACK);
		Paragraph paragraph = new Paragraph(text, paragraphFont);
		document.add(paragraph);
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
		table.setSpacingBefore(5f);
		table.setSpacingAfter(5f);
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

}
