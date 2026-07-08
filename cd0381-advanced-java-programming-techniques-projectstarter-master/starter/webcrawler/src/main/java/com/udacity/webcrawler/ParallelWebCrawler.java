package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.*;
import java.util.regex.Pattern;

import java.util.concurrent.atomic.AtomicBoolean;
/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int maxDepth;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final Provider<PageParserFactory> parserFactory;
  private final Set<String> vis = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();
  private final List<Pattern> ignoredUrlPatterns;
  private Instant endTime;

  @Inject
  ParallelWebCrawler(
          Clock clock,
          @Timeout Duration timeout,
          @PopularWordCount int popularWordCount,
          @TargetParallelism int threadCount,
          @MaxDepth int maxDepth,
          @IgnoredUrls List<Pattern> ignoredUrlPatterns,
          Provider<PageParserFactory> parserFactory) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.ignoredUrlPatterns=ignoredUrlPatterns;
    this.parserFactory = parserFactory;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.maxDepth = maxDepth;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    this.endTime = clock.instant().plus(timeout);
    try{
    List<CrawlTask> tasks = startingUrls.stream().filter(url->maxDepth > 0).map(url->new CrawlTask(url,1)).toList();
    pool.invoke(new MasterTask(tasks));
      Map<String, Integer>topWords = WordCounts.sort(wordCounts,popularWordCount);
    return new CrawlResult.Builder().setWordCounts(topWords).setUrlsVisited(vis.size()).build();}
    finally{
      pool.shutdown();
    }
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  private final class MasterTask extends RecursiveAction{
    private final List<CrawlTask> tasks;
    MasterTask(List<CrawlTask> tasks){
      this.tasks= tasks;
    }
    @Override
    protected void compute(){
      invokeAll(tasks);
    }
  }
  private final class CrawlTask extends RecursiveAction {
    //private final List<String>urls;
    private final String url;
    private final int depth;
    //private final boolean isRoot;

    /* CrawlTask(List<String> urls, int depth) {
      this.urls = urls;
      this.depth = depth;
      this.url=null;
      this.isRoot=true;

    }*/
CrawlTask(String url,int depth){
      // this.urls=null;
       this.url=url;
       this.depth=depth;
       //this.isRoot=false;
}
    @Override
    protected void compute() {
      if(depth > maxDepth){
        return;
      }
      if (clock.instant().isAfter(endTime)) {
        return;
      }
      if(url == null || url.isBlank()){
        return;
      }
      for(Pattern p:ignoredUrlPatterns) {
        if (p.matcher(url).matches()) {
          return;
        }
      }
      if (!vis.add(url)){
        return;
      }
        //  if (!vis.add(url)) return;
          try {
            PageParser parser = parserFactory.get().get(url);
            PageParser.Result result = parser.parse();
            //result.getWordCounts().forEach((word, count) -> wordCounts.merge(word, count, Integer::sum));
            for(Map.Entry<String,Integer> entry : result.getWordCounts().entrySet()){
              String word = entry.getKey();
              Integer count = entry.getValue();
              if(word == null) continue;
              word = word.trim();
              if(word.isEmpty()) continue;
              wordCounts.merge(word,count,Integer::sum);
            }
            List<CrawlTask>subtasks = new ArrayList<>();
            for(String link : result.getLinks()) {
              subtasks.add(new CrawlTask(link, depth + 1));
            }
            invokeAll(subtasks);
          }
          catch(Exception e){
            e.printStackTrace();
            }
          }
        }
    }