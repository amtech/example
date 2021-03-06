package com.sinosoft.one.showcase.component.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sinosoft.one.cache.CacheBuilderSelector;
import com.sinosoft.one.cache.CacheLoader;
import com.sinosoft.one.cache.LoadingCache;

public class LoadingCacheTest {
	
	private static final Logger logger=LoggerFactory.getLogger(LoadingCacheTest.class);
	private LoadingCache< String, String> loaderCache;
	@Before
	public void initCache() {
		//针对整个cache定义一个原子操作，根据给定的key获取value，需要重写原子操作load(V key)
		//使用默认的同步方式的reload(K key, V oldValue)方法
		loaderCache=CacheBuilderSelector.newBuilder("cache.properties")
				.expireAfterWrite(100, TimeUnit.SECONDS)
				.build(new CacheLoader<String, String> (){
					@Override
					public String load(String key) {
						return "abc";
					}
				});
	}
	
	//测试get方法，使用CacheLoader指定原子操作，自填充
	@Test
	public void testGet() throws ExecutionException{
		String value=loaderCache.get("a");
		Assert.assertEquals("abc", value);
	}
	//测试getAll，指定keys
	@Test
	public void testgetAll() throws ExecutionException{
		Map<String, String> map=new HashMap<String, String>();
		map.put("a", "1");
		map.put("b", "2");
		map.put("c", "3");
		loaderCache.putAll(map);
		List<String> list=new ArrayList<String>();
		list.add("a");
		list.add("b");
		Iterable<String> keys=list;
		Map<String, String> result=loaderCache.getAll(keys);
		Assert.assertEquals("1", result.get("a"));
		Assert.assertEquals("2", result.get("b"));
		Assert.assertEquals(null, result.get("c"));
		Assert.assertEquals(2, result.size());
	}
	//测试refresh
	@SuppressWarnings("static-access")
	@Test
	public void testRefresh() throws ExecutionException, InterruptedException{
		final LoadingCache< String, String> cache=CacheBuilderSelector.newBuilder("cache.properties")
				.expireAfterWrite(10000, TimeUnit.SECONDS)
				.refreshAfterWrite(4, TimeUnit.SECONDS)
				.build(new CacheLoader<String, String> (){
					@Override
					public String load(String key) {
						long value=System.currentTimeMillis();
						logger.info("from load(),value:::"+value);
						return String.valueOf(value);
					}					
				});
		//初始时，缓存内不包括key=a的键值对，此时cache.get()会调用CacheLoader.load()
		String beforeRefreshed=cache.get("a");
		Runnable runnable=new Runnable() {
			public void run() {
				logger.info(Thread.currentThread().getName()+":::start");
				//此时cache.refresh()会调用CacheLoader.load()
				cache.refresh("a");
				try {
					logger.info(Thread.currentThread().getName()+":::end,value:::"+cache.get("a"));
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
		};
		//开启多个线程同时刷新缓存数据，只有一个得到刷新后的value，其余的线程得到旧的value
		for(int i=0;i<5;i++){
			Thread thread=new Thread(runnable, "thread"+i);
			thread.start();
		}
		Thread.currentThread().sleep(1*1000);
		String afterRefreshed=cache.get("a");
		logger.info("after refreshed value:::"+afterRefreshed);
		Assert.assertNotSame(beforeRefreshed, afterRefreshed);
		Thread.currentThread().sleep(5*1000);
		logger.info("end");
	}

}
