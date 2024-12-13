package com.sushil.elasticsearch.others;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.BucketMetricValueAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

public class SyntheticIndexHourly {
    public static void mainSyntheticIndexHourly(String[] args) {
        final String username = "elastic";
        final String password = "Clover@123";
        final String host = "10.100.0.199";
        final int port = 9200;
        final String filePath="src/main/resources/synthetic_hourly_uptime.json";

        // 1. Configure Basic Authentication credentials
        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
        credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        // 2. Create the low-level REST client with credentials
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credsProv));
        RestClient restClient = builder.build();

        // Create the transport layer using Jackson for JSON mapping
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

        ElasticsearchClient client = new ElasticsearchClient(transport);

        try {
            executeSearchQuery(client);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the client
            closeClient(restClient);
        }
    }

    public static void executeSearchQuery(ElasticsearchClient client) throws IOException {
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index("synthetic_index")
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.range(r -> r
                                .date(d -> d.field("@timestamp").gte("now-1h").lte("now"))
                        ))
                ))
                .aggregations("group_by_url", a -> a
                        .terms(t -> t
                                .field("url.keyword")
                                .size(10) // Top 10 URLs
                        )
                        .aggregations("total_count", agg -> agg
                                .valueCount(vc -> vc.field("email_status.keyword"))
                        )
                        .aggregations("working_count", agg -> agg
                                .filter(f -> f
                                        .term(t -> t
                                                .field("email_status.keyword")
                                                .value("N/A")
                                        )
                                )
                        )
                        .aggregations("not_working_count", agg -> agg
                                .filter(f -> f
                                        .term(t -> t
                                                .field("email_status.keyword")
                                                .value("Success")
                                        )
                                )
                        )
                        .aggregations("uptime_percentage", agg -> agg
                                .bucketScript(bs -> bs
                                        .bucketsPath(bp -> bp
                                                .dict(Map.of("total", "total_count", "working", "working_count._count"))
                                        )
                                        .script(script -> script.lang(ScriptLanguage.Painless).source("100 * (params.working / params.total)").params(Map.of(
                                                "total_count", JsonData.of("total_count"),
                                                "working_count", JsonData.of("working_count._count")
                                        )))
                                )
                        )
                )
        );
        SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);
        displayUptimePercentage(searchResponse);
    }

    public static void displayUptimePercentage(SearchResponse<Map> searchResponse) {
    	System.out.println("Report Generated At : " + formatDate(Instant.now().toString()) + " for last hour" );
        Map<String, Aggregate> aggregations = searchResponse.aggregations();
        Aggregate groupByUrlAggregation = aggregations.get("group_by_url");
        if (groupByUrlAggregation != null) {
            List<StringTermsBucket> buckets = groupByUrlAggregation.sterms().buckets().array();
            for (StringTermsBucket bucket : buckets) {
                String url = bucket.key().stringValue();
                Aggregate uptimePercentageAggregation = bucket.aggregations().get("uptime_percentage");
                if (uptimePercentageAggregation != null) {
                    Double uptimePercentage = uptimePercentageAggregation.simpleValue().value();
					System.out.println("URL: " + url + " UPTIME_PERCENTAGE: " + uptimePercentage);
                } else {
                    System.out.println("URL: " + url + " UPTIME_PERCENTAGE: N/A");
                }
            }
        } else {
            System.out.println("No group_by_url aggregation found.");
        }
    }

    private static void closeClient(RestClient restClient) {
        try {
            restClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static String formatDate(String dateString) {
        Instant instant = Instant.parse(dateString);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a")
        		.withZone(ZoneId.of("Asia/Kolkata"));
        return formatter.format(instant);
    }
}
