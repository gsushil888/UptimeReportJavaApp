package com.sushil.elasticsearch.others;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class UptimeIndexAll {
	public static void mainUptimeIndexAll(String[] args) {
		final String username = "elastic";
		final String password = "Clover@123";
		final String host = "10.100.0.199";
		final int port = 9200;

		BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
		credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

		RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http")).setHttpClientConfigCallback(
				httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProv));

		RestClient restClient = builder.build();

		ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

		ElasticsearchClient client = new ElasticsearchClient(transport);

		try {
			executeSearchQuery(client);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				restClient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void executeSearchQuery(ElasticsearchClient client) throws IOException {

		try {
			MatchAllQuery matchAllQuery = QueryBuilders.matchAll().build();
			SearchRequest searchRequest = SearchRequest.of(s -> s.index("uptime_index").size(0)
					.query(query -> query.matchAll(matchAllQuery)).aggregations("hourly_avg",
							agg -> agg.dateHistogram(d -> d.field("@timestamp").calendarInterval(CalendarInterval.Hour))
									.aggregations("avg_uptime",
											agg2 -> agg2.avg(avg -> avg.field("uptime_percentage")))));
			SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
			System.out.println();
			List<DateHistogramBucket> buckets = searchResponse.aggregations().get("hourly_avg").dateHistogram()
					.buckets().array();
			for (DateHistogramBucket bucket : buckets) {
				System.out.println("TIMESTAMP = " + formatDate(bucket.keyAsString()) + " || UPTIME PERCENTAGE = "
						+ bucket.aggregations().get("avg_uptime").avg().value());
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String formatDate(String dateString) {
		Instant instant = Instant.parse(dateString);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")
				.withZone(ZoneId.systemDefault());
		return formatter.format(instant);
	}
}
