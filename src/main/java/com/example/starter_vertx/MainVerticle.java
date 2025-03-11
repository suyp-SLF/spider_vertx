package com.example.starter_vertx;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.bson.conversions.Bson;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {

  private static MongoClient mongoClient;

  static {
    System.out.println("===============MongoDBUtil初始化========================");
    mongoClient = new MongoClient("127.0.0.1", 27017);
    // 大部分用户使用mongodb都在安全内网下，但如果将mongodb设为安全验证模式，就需要在客户端提供用户名和密码：
    // boolean auth = db.authenticate(myUserName, myPassword);
    MongoClientOptions.Builder options = new MongoClientOptions.Builder();
    options.cursorFinalizerEnabled(true);
    // options.autoConnectRetry(true);// 自动重连true
    // options.maxAutoConnectRetryTime(10); // the maximum auto connect retry time
    options.connectionsPerHost(300);// 连接池设置为300个连接,默认为100
    options.connectTimeout(30000);// 连接超时，推荐>3000毫秒
    options.maxWaitTime(5000); //
    options.socketTimeout(0);// 套接字超时时间，0无限制
    options.threadsAllowedToBlockForConnectionMultiplier(5000);// 线程队列数，如果连接线程排满了队列就会抛出“Out of semaphores to get db”错误。
    options.writeConcern(WriteConcern.SAFE);//
    options.build();
  }

  public void start(Future<Void> startFuture) throws Exception {
    VertxOptions options = new VertxOptions().
      setWorkerPoolSize(40);
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

//    WebClient client;
//    WebClientOptions webclientoptions = new WebClientOptions()
//      .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.119 Safari/537.36");
//    webclientoptions.setKeepAlive(false);
//    client = WebClient.create(vertx, webclientoptions);

    Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add("x-requested-with");
    allowedHeaders.add("Access-Control-Allow-Origin");
    allowedHeaders.add("origin");
    allowedHeaders.add("Content-Type");
    allowedHeaders.add("accept");
    allowedHeaders.add("X-PINGARUNER");

    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    /*
     * these methods aren't necessary for this sample,
     * but you may need them for your projects
     */
    allowedMethods.add(HttpMethod.DELETE);
    allowedMethods.add(HttpMethod.PATCH);
    allowedMethods.add(HttpMethod.PUT);


    /*测试用例*/
    Route questionInRoute = router.route(HttpMethod.POST, "/questionin").handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    Route questionSearchRoute = router.route(HttpMethod.POST, "/questionsearch").handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    Route contentRoute = router.route(HttpMethod.POST, "/testcontent").handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));

    /*文章抓取*/
    Route articlespider = router.route(HttpMethod.POST, "/articlespider");
    /*获取html*/
    Route sendurl = router.route(HttpMethod.POST, "/sendurl");

    contentRoute.handler(BodyHandler.create())
      .handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/json");
        response.putHeader("Access-Control-Allow-Origin","*");
        response.putHeader("Cache-Control","no-cache");
        response.end("OK");
  });

    questionInRoute.handler(BodyHandler.create())
      .handler(routingContext -> {
        int rightCount = 0;
        int errorCount = 0;
        HttpServerResponse response = routingContext.response();
        Buffer buffer = routingContext.getBody();
        String html = null;
        try {
          html = URLDecoder.decode(buffer.toString().substring(10), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        Document doc = Jsoup.parse(html);
        Elements questions = doc.getElementsByClass("question");
        List<org.bson.Document> documents = new ArrayList<>();
        for (Element question: questions) {
          org.bson.Document document = new org.bson.Document();
          String id = question.attr("id");

          document.append("pkvalue", id);

          document.append("html", question.html());

          System.out.println(question.html());
          String subject = question.getElementsByClass("question-title").get(0).text();//题目
          document.append("subject", subject);

          List<String> optionStrList = new ArrayList<>();
          Elements optiones = question.getElementsByClass("optionCheck");
          for (Element option: optiones) {
            String optionStr = option.text();
            optionStrList.add(optionStr);
          }
          document.append("options", optionStrList);

          if(haveRight(subject, optionStrList)){
            continue;
          }

          List<String> resultStrList = new ArrayList<>();
          Elements results = question.getElementsByClass("ivu-col-span-24");
          for (Element result: results) {
            String resultStr = result.text();
            resultStrList.add(resultStr);
          }
          document.append("result", resultStrList);

          Boolean isRight = resultStrList.contains("该题回答:正确");
          if(isRight) {
            rightCount++;
            //删除错误答案
            deleteQuestion(subject);
            //todo
            document.append("isRight", true);
            documents.add(document);
          }else {
            errorCount++;
            document.append("isRight", false);
            documents.add(document);
          }
        }
        if(documents.size() > 0)
          saveQuestion(documents);
        response.putHeader("content-type", "text/json");
        response.end("共收录" + rightCount + "条正确问题！" + errorCount + "条错误问题！");
      });

    questionSearchRoute.handler(BodyHandler.create())
      .handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        Buffer buffer = routingContext.getBody();
        String html = null;
        try {
          html = URLDecoder.decode(buffer.toString().substring(10), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        Document doc = Jsoup.parse(html);
        Elements questions = doc.getElementsByClass("question");
        List<org.bson.Document> documents = new ArrayList<>();
        JSONObject resJson = new JSONObject();

        int rightCount = 0;
        int errorCount = 0;
        int noneCount = 0;
        JSONArray questionsJson = new JSONArray();
        for (Element question: questions) {
          JSONObject questionJson = new JSONObject();
          String subject = question.getElementsByClass("question-title").get(0).text();//题目
          String id = question.attr("id");

          List<String> optionStrList = new ArrayList<>();
          Elements optiones = question.getElementsByClass("optionCheck");
          for (Element option: optiones) {
            String optionStr = option.text();
            optionStrList.add(optionStr);
          }

          JSONArray htmlJson = findConditionTest(subject, optionStrList);

          if (htmlJson.size() == 0 ){
            noneCount++;
          }else if(htmlJson.getJSONObject(0).getBoolean("isRight")){
            rightCount++;
          }else {
            errorCount++;
          }

          questionJson.put("subject", subject);
          questionJson.put("result", htmlJson);
          questionJson.put("id", id);

          questionsJson.add(questionJson);
        }

        resJson.put("answer", questionsJson);
        resJson.put("rightCount", rightCount);
        resJson.put("errorCount", errorCount);
        resJson.put("noneCount", noneCount);
        response.putHeader("content-type", "text/json");
        response.end(resJson.toJSONString());
      });

    articlespider
      .handler(BodyHandler.create())
      .handler(routingContext -> {
        HttpServerResponse response = routingContext.response();
        System.out.println(routingContext.getBodyAsJson().getString("name"));
        response.putHeader("content-type", "text/json");
        response.end();
      });

//    sendurl
//      .handler(BodyHandler.create())
//      .handler(routingContext -> {
//        HttpServerResponse routresponse = routingContext.response();
//        JsonObject jo = routingContext.getBodyAsJson();
//
//        client.get("www.baidu.com", "/").send(ar -> {
//          if (ar.succeeded()) {
//            // 获取响应
//            HttpResponse<Buffer> response = ar.result();
//            routresponse.end(response.body());
//            System.out.println("Received response with status code" + response.statusCode());
//          } else {
//            System.out.println("Something went wrong " + ar.cause().getMessage());
//          }
//        });
//      });
//
    server.requestHandler(router::accept).listen(8090);
  }

  private void saveQuestion(List<org.bson.Document> documents){
    //获取集合
    MongoCollection<org.bson.Document> collection = getCollection("questions","question");

    //插入一个文档
    collection.insertMany(documents);
  }

  private Boolean haveRight(String subject, List<String> options){
    JSONArray questions = new JSONArray();
    MongoCollection<org.bson.Document> collection = getCollection("questions","question");
    //方法1.构建BasicDBObject  查询条件 id大于2，小于5
    BasicDBObject queryCondition=new BasicDBObject();
    queryCondition.put("subject", new BasicDBObject("$eq", subject));
    queryCondition.put("isRight", new BasicDBObject("$eq", true));
    queryCondition.put("options", new BasicDBObject("$eq", options));
    //查询集合的所有文  通过price升序排序
    FindIterable findIterable= collection.find(queryCondition).sort(Sorts.orderBy(Sorts.ascending("isRight")));

    //方法2.通过过滤器Filters，Filters提供了一系列查询条件的静态方法   id大于2小于5   通过id升序排序查询
    //Bson filter=Filters.and(Filters.gt("id", 2),Filters.lt("id", 5));
    //FindIterable findIterable= collection.find(filter).sort(Sorts.orderBy(Sorts.ascending("id")));

    //查询集合的所有文
    MongoCursor cursor = findIterable.iterator();

    if(cursor.hasNext()){
      return true;
    }else {
      return false;
    }
  }

  public JSONArray findConditionTest(String subject,List<String> options){
    //获取集合
    JSONArray questions = new JSONArray();
    MongoCollection<org.bson.Document> collection = getCollection("questions","question");
    //方法1.构建BasicDBObject  查询条件 id大于2，小于5
    BasicDBObject queryCondition=new BasicDBObject();
    queryCondition.put("subject", new BasicDBObject("$eq", subject));
    queryCondition.put("options", new BasicDBObject("$eq", options));
    //查询集合的所有文  通过price升序排序
    FindIterable findIterable= collection.find(queryCondition);

    //方法2.通过过滤器Filters，Filters提供了一系列查询条件的静态方法   id大于2小于5   通过id升序排序查询
    //Bson filter=Filters.and(Filters.gt("id", 2),Filters.lt("id", 5));
    //FindIterable findIterable= collection.find(filter).sort(Sorts.orderBy(Sorts.ascending("id")));

    //查询集合的所有文
    MongoCursor cursor = findIterable.iterator();
    while (cursor.hasNext()) {
      JSONObject question = new JSONObject();
      org.bson.Document item = (org.bson.Document) cursor.next();
      question.put("html", item.getString("html"));
      question.put("isRight", item.getBoolean("isRight"));
      questions.add(question);
    }
    return questions;
  }

  private void deleteQuestion(String subject){
    //获取集合
    MongoCollection<org.bson.Document> collection = getCollection("questions","question");
    //申明删除条件
    Bson filter = Filters.eq("subject",subject);
    //删除与筛选器匹配的单个文档
//    collection.deleteOne(filter);

    //删除与筛选器匹配的所有文档
     collection.deleteMany(filter);

//    System.out.println("--------删除所有文档----------");
    //删除所有文档
    // collection.deleteMany(new Document());
  }

  /**
   * 获取指定数据库下的collection对象
   * @param collName
   * @return
   */
  public static  MongoCollection<org.bson.Document> getCollection(String dbName, String collName) {
    if (null == collName || "".equals(collName)) {
      return null;
    }
    if (null == dbName || "".equals(dbName)) {
      return null;
    }
    MongoCollection<org.bson.Document> collection = mongoClient.getDatabase(dbName).getCollection(collName);
    return collection;
  }


}
