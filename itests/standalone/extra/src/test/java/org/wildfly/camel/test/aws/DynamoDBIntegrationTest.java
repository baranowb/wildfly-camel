package org.wildfly.camel.test.aws;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.camel.builder.RouteBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.aws.subA.DynamoDBClientProducer;
import org.wildfly.camel.test.aws.subA.DynamoDBClientProducer.DynamoDBClientProvider;
import org.wildfly.camel.test.common.aws.BasicCredentialsProvider;
import org.wildfly.camel.test.common.aws.DynamoDBUtils;
import org.wildfly.camel.test.common.types.CatalogItem;
import org.wildfly.camel.test.common.types.TableNameProviderA;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.WildFlyCamelContext;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

@CamelAware
@RunWith(Arquillian.class)
public class DynamoDBIntegrationTest {

    @Inject
    private String tableName;

    @Inject
    private DynamoDBClientProvider provider;
    
    @Deployment
    public static JavaArchive deployment() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "aws-ddb-tests.jar");
        archive.addClasses(DynamoDBClientProducer.class, DynamoDBUtils.class, BasicCredentialsProvider.class, CatalogItem.class, TableNameProviderA.class);
        archive.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        return archive;
    }
    
    @Test
    @Ignore("[#1778] Add support for DynamoDB mapper")
    public void testMapperOperations() throws Exception {

        AmazonDynamoDBClient client = provider.getClient();
        Assume.assumeNotNull("AWS client not null", client);

        DynamoDBMapper mapper = new DynamoDBMapper(client);

        CatalogItem item = new CatalogItem();
        item.setId(102);
        item.setTitle("Book 102 Title");
        item.setISBN("222-2222222222");
        item.setBookAuthors(new HashSet<String>(Arrays.asList("Author 1", "Author 2")));
        item.setSomeProp("Test");

        mapper.save(item);

        item = new CatalogItem();
        item.setId(102);

        DynamoDBQueryExpression<CatalogItem> exp = new DynamoDBQueryExpression<CatalogItem>().withHashKeyValues(item);

        List<CatalogItem> result = mapper.query(CatalogItem.class, exp);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(new Integer(102), result.get(0).getId());
        Assert.assertEquals("Book 102 Title", result.get(0).getTitle());
    }

    @Test
    public void testKeyValueOperations() throws Exception {
        
        AmazonDynamoDBClient client = provider.getClient();
        Assume.assumeNotNull("AWS client not null", client);
        
        WildFlyCamelContext camelctx = new WildFlyCamelContext();
        camelctx.getNamingContext().bind("ddbClientA", client);
        
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("aws-ddb://" + tableName + "?amazonDDBClient=#ddbClientA");
            }
        });

        camelctx.start();
        try {
            DynamoDBUtils.putItem(camelctx, "Book 103 Title");
            
            String result = ((AttributeValue) DynamoDBUtils.getItem(camelctx).get("Title")).getS();
            Assert.assertEquals("Book 103 Title", result);

            DynamoDBUtils.updItem(camelctx, "Book 103 Update");

            result = ((AttributeValue) DynamoDBUtils.getItem(camelctx).get("Title")).getS();
            Assert.assertEquals("Book 103 Update", result);
                
        } finally {
            camelctx.stop();
        }
    }

}
