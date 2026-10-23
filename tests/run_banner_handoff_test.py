"""Exercise the compiled SDK manager; replace Banner rendering/transport, not its handoff logic."""
from pathlib import Path
import runpy,subprocess
b=runpy.run_path(str(Path(__file__).with_name('run_ump_gate_test.py')))
p=b['p'];j=b['java_home'];cp=str(p/'classes')+':'+b['cp']
files={
'com/joyboost/moneyboost/MoneyBoostBannerAdapter.java':'''package com.joyboost.moneyboost;
public class MoneyBoostBannerAdapter {
 public String MoneyBoost_ad_unit;public boolean ready=true,visible,failShow;public static java.util.List<String> calls=new java.util.ArrayList<>();
 public boolean MoneyBoostIsLoaded(){return ready;}public boolean MoneyBoostIsVisible(){return visible;}
 public void MoneyBoostShowBannerView(){calls.add(MoneyBoost_ad_unit+":show");if(!failShow)visible=true;}
 public void MoneyBoostHideBannerView(){calls.add(MoneyBoost_ad_unit+":hide");visible=false;}
 public void MoneyBoostReleaseDisplayedBanner(){calls.add(MoneyBoost_ad_unit+":release");visible=false;}
 public void MoneyBoostOnDestroy(){calls.add(MoneyBoost_ad_unit+":destroy");visible=false;}
}''',
'com/joyboost/moneyboost/BannerHandoffTest.java':'''package com.joyboost.moneyboost;
import java.lang.reflect.*;import java.util.*;
public class BannerHandoffTest{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void set(Object t,String n,Object v)throws Exception{Field f=t.getClass().getDeclaredField(n);f.setAccessible(true);f.set(t,v);}
 static Object get(Object t,String n)throws Exception{Field f=t.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(t);}
 static void sync(MoneyBoostMediationManager m)throws Exception{Method f=m.getClass().getDeclaredMethod("MoneyBoostSyncBannerVisibility");f.setAccessible(true);f.invoke(m);}
 public static void main(String[]args)throws Exception{
 MoneyBoostMediationManager m=MoneyBoostMediationManager.getInstance();
 set(m,"MoneyBoost_adsPermitted",true);set(m,"MoneyBoost_bannerEnabled",true);set(m,"MoneyBoost_needPopAD",true);set(m,"MoneyBoost_isAppForeground",true);set(m,"MoneyBoost_isDestroyed",false);
 MoneyBoostBannerAdapter regular=new MoneyBoostBannerAdapter(),fold=new MoneyBoostBannerAdapter();regular.MoneyBoost_ad_unit="regular";fold.MoneyBoost_ad_unit="fold";
 set(m,"MoneyBoost_bannerAdapter",regular);set(m,"MoneyBoost_collapsibleAdapter",fold);set(m,"MoneyBoost_collapsibleActive",true);
 fold.ready=false;sync(m);check(regular.visible&&!fold.visible,"pending fold hid ordinary Banner");
 fold.ready=true;fold.failShow=true;sync(m);check(regular.visible&&!fold.visible,"failed fold registration hid Banner");
 fold.failShow=false;MoneyBoostBannerAdapter.calls.clear();sync(m);
 check(fold.visible&&!regular.visible,"fold not shown");check(MoneyBoostBannerAdapter.calls.indexOf("fold:show")<MoneyBoostBannerAdapter.calls.indexOf("regular:hide"),"ordinary hidden before fold show");
 Object analytics=get(m,"adAnalytics");Method opportunity=analytics.getClass().getDeclaredMethod("opportunity",String.class,String.class,boolean.class);opportunity.setAccessible(true);
 MoneyBoostFirebaseManager.events.clear();MoneyBoostFirebaseManager.payloads.clear();
 set(m,"bannerOpportunity",opportunity.invoke(analytics,"banner","persistent",true));set(m,"collapsibleOpportunity",opportunity.invoke(analytics,"collapsibleBanner","action_menu",true));
 m.MoneyBoostOnBannerImpression(regular,7,false);m.MoneyBoostOnBannerImpression(regular,7,false);
 m.MoneyBoostOnBannerImpression(fold,7,true);m.MoneyBoostOnBannerImpression(fold,7,true);m.MoneyBoostOnBannerImpression(fold,8,false);
 check(java.util.Collections.frequency(MoneyBoostFirebaseManager.events,"banner_show")==1,"ordinary counted twice");
 check(java.util.Collections.frequency(MoneyBoostFirebaseManager.events,"collapsibleBanner_show")==1,"fold counted twice");
 check(java.util.Collections.frequency(MoneyBoostFirebaseManager.events,"collapsibleBanner_fallback_show")==1,"fallback not separated");
 for(int i=0;i<MoneyBoostFirebaseManager.events.size();i++){
  String event=MoneyBoostFirebaseManager.events.get(i);android.os.Bundle payload=MoneyBoostFirebaseManager.payloads.get(i);
  if(event.equals("banner_show"))check(payload.getString("ad_unit_id").equals("regular"),"ordinary unit wrong");
  if(event.equals("collapsibleBanner_show")||event.equals("collapsibleBanner_fallback_show"))check(payload.getString("ad_unit_id").equals("fold"),"fold unit wrong");
  check(!payload.containsKey("value")&&!payload.containsKey("currency"),"count duplicated revenue");
 }
 MoneyBoostBannerAdapter.calls.clear();m.MoneyBoostHideCollapsibleBannerView();check(regular.visible,"fold close lost ordinary Banner");
 check(MoneyBoostBannerAdapter.calls.indexOf("regular:show")<MoneyBoostBannerAdapter.calls.indexOf("fold:release"),"fold released before ordinary restored");
 set(m,"MoneyBoost_isAppForeground",false);sync(m);check(!regular.visible,"background Banner visible");
 System.out.println("BANNER HANDOFF PASS: separate IDs, pending/failed fold retains ordinary, show-before-hide, restore-before-destroy, per-unit impression dedup, fallback split, count-only events, background exclusion.");
 }
}'''
}
for name,body in files.items():
 f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
subprocess.run([str(j/'bin/javac'),'-cp',cp,'-d',str(p/'classes'),*[str(p/n) for n in files]],check=True)
subprocess.run([str(j/'bin/java'),'-cp',cp,'com.joyboost.moneyboost.BannerHandoffTest'],check=True)
