package com.sushil.elasticsearch.others;

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
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;

public class UpTimeIndexAll8To8Test {

	private static final String USERNAME = "elastic";
	private static final String PASSWORD = "Clover@123";
	private static final String HOST = "10.100.0.199";
	private static final int PORT = 9200;

	public static void mainUpTimeIndexAll8To8Test(String[] args) {
		try (RestClient restClient = createRestClient()) {
			ElasticsearchClient client = createElasticsearchClient(restClient);
			executeSearchQuery(client);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// RestClient creation
	private static RestClient createRestClient() {
		BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));
		return RestClient.builder(new HttpHost(HOST, PORT, "http")).setHttpClientConfigCallback(
				httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProvider)).build();
	}

	// ElasticsearchClient creation
	private static ElasticsearchClient createElasticsearchClient(RestClient restClient) {
		ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
		return new ElasticsearchClient(transport);
	}

	private static void executeSearchQuery(ElasticsearchClient client) throws IOException {
		// Load the query file from resources
		InputStream queryStream = UpTimeIndexAll8To8.class.getClassLoader()
				.getResourceAsStream("uptime_day_hourly_8to8.json");
		if (queryStream == null) {
			throw new IllegalArgumentException("JSON file not found: uptime_day_hourly_8to8.json");
		}

		// Use JacksonJsonpMapper to parse JSON
		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		JsonParser queryParser = jsonpMapper.jsonProvider().createParser(queryStream);

		// Build and execute the search request
		SearchRequest searchRequest = SearchRequest.of(b -> b.index("uptime_index").withJson(queryParser, jsonpMapper));
		SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

		// Generate PDF using the search response
		generatePdf(searchResponse);
	}

	// Loads the JSON query from the resources folder
	private static JsonParser loadQuery(String fileName) {
		InputStream queryStream = UpTimeIndexAll8To8.class.getClassLoader().getResourceAsStream(fileName);
		if (queryStream == null)
			throw new IllegalArgumentException("JSON file not found: " + fileName);

		JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
		return jsonpMapper.jsonProvider().createParser(queryStream);
	}

	// Formats the given date string into "dd-MMM-yyyy hh:mm:ss a" format
	private static String formatDate(String dateString) {
		Instant instant = Instant.parse(dateString);
		return DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a").withZone(ZoneId.of("Asia/Kolkata"))
				.format(instant);
	}

	// Generates PDF report from the search response
	private static void generatePdf(SearchResponse<Map> searchResponse) {
		final String pdfFilePath = "Uptime_Report.pdf";
		try (FileOutputStream fos = new FileOutputStream(pdfFilePath)) {
			Document document = new Document();
			PdfWriter.getInstance(document, fos);

			document.open();
			addPdfTitle(document);
			addPdfTable(document, searchResponse);

			document.close();
			System.out.println("PDF successfully generated at: " + pdfFilePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Adds title to the PDF with current timestamp
	private static void addPdfTitle(Document document) throws DocumentException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss a");
		String currentDateTime = sdf.format(new Date());

		Paragraph title = new Paragraph("Uptime Percentage Report Per Day\tDate - " + currentDateTime + " IST");
		title.setAlignment(Element.ALIGN_CENTER);
		title.setSpacingAfter(20f);
		document.add(title);
	}

	// Adds table with aggregated results to the PDF
	private static void addPdfTable(Document document, SearchResponse<Map> searchResponse) throws DocumentException {
		PdfPTable table = new PdfPTable(3);
		table.setWidthPercentage(100);
		table.setSpacingBefore(10f);

		// Add table headers
		table.addCell("Date Timestamp");
		table.addCell("URL");
		table.addCell("Uptime Percentage");

		// Extract and add table data
		Map<String, Aggregate> aggregations = searchResponse.aggregations();
		Aggregate groupByUrl = aggregations.get("group_by_url");

		for (StringTermsBucket bucket : groupByUrl.sterms().buckets().array()) {
			String url = bucket.key().stringValue();
			Aggregate hourlyAvgAgg = bucket.aggregations().get("hourly_avg");

			for (DateHistogramBucket dateBucket : hourlyAvgAgg.dateHistogram().buckets().array()) {
				String timestamp = formatDate(dateBucket.keyAsString());
				Double uptimeAvg = dateBucket.aggregations().get("avg_uptime").avg().value();

				table.addCell(timestamp);
				table.addCell(url);
				table.addCell(String.valueOf(uptimeAvg));
			}
		}
		document.add(table);
	}
}
