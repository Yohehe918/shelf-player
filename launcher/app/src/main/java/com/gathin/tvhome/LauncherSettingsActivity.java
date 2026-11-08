package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Two-column, remote-first settings. Values use the existing Config/return-to-Home contract. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER="com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences prefs;
    private LinearLayout detail, navigation;
    private TextView heading, subtitle;
    private int section;
    private final String[] categories={"外观","主屏幕","屏幕保护","电视与设备","关于"};
    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(LauncherLocale.wrap(base)); }
    int d(float n){ return TvStyle.dp(this,n); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences(Config.SETTINGS_NAME,0);
        LinearLayout root=new LinearLayout(this); TvStyle.sceneBackground(this,root); root.setPadding(d(56),d(42),d(56),d(36));
        setContentView(root);
        LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setPadding(0,d(8),d(38),0);
        root.addView(left,new LinearLayout.LayoutParams(d(280),-1));
        left.addView(TvStyle.text(this,"设置",42,TvStyle.INK));
        TextView caption=TvStyle.text(this,"CleanYourTV",15,TvStyle.SECONDARY); caption.setPadding(0,d(14),0,d(34)); left.addView(caption);
        navigation=new LinearLayout(this); navigation.setOrientation(LinearLayout.VERTICAL); left.addView(navigation);
        for(int i=0;i<categories.length;i++){
            final int index=i; TextView item=TvStyle.text(this,categories[i],19,TvStyle.INK);
            item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(d(20),0,d(20),0); TvStyle.focusText(item); item.setId(View.generateViewId());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(52)); lp.bottomMargin=d(6); navigation.addView(item,lp);
            item.setOnFocusChangeListener((v,f)->{if(f && section!=index) showSection(index);});
            item.setOnClickListener(v->focusDetail());
            item.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false; if(key==KeyEvent.KEYCODE_DPAD_RIGHT){focusDetail();return true;}return key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_UP&&index==0 || key==KeyEvent.KEYCODE_DPAD_DOWN&&index==categories.length-1;});
        }
        LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(d(38),d(9),0,0); root.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        heading=TvStyle.text(this,"",32,TvStyle.INK); right.addView(heading);
        subtitle=TvStyle.text(this,"",14,TvStyle.SECONDARY); subtitle.setPadding(0,d(12),0,d(22)); right.addView(subtitle);
        ScrollView scroll=new ScrollView(this); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false); right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        detail=new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL); detail.setBackground(TvStyle.surface(0x08FFFFFF,12,this));detail.setClipToOutline(true); detail.setPadding(d(5),d(5),d(5),d(12)); scroll.addView(detail);
        showSection(state==null?0:state.getInt("section",0)); navigation.getChildAt(section).requestFocus();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("section",section);}
    private void focusDetail(){for(int i=0;i<detail.getChildCount();i++)if(detail.getChildAt(i).isFocusable()){detail.getChildAt(i).requestFocus();break;}}
    private void showSection(int which){
        section=which; detail.removeAllViews(); heading.setText(categories[which]);
        for(int i=0;i<navigation.getChildCount();i++)navigation.getChildAt(i).setSelected(i==which);
        Config cfg=Config.load(this);
        switch(which){
        case 0:
            subtitle.setText("背景、卡片和焦点的统一外观");
            row("背景",prefs.getBoolean("scene_off",false)?"深空黑":"阿尔卑斯山",()->choose("背景",new String[]{"阿尔卑斯山","深空黑"},prefs.getBoolean("scene_off",false)?1:0,i->save("scene_off",i==1)));
            row("每行应用",cfg.columns+" 个",()->choose("每行应用",new String[]{"4 个","5 个","6 个","7 个"},cfg.columns-4,i->save("columns",i+4)));
            row("常用栏透明度",cfg.dockOpacity+"%",()->choose("常用栏透明度",new String[]{"30%","50%","70%","100%"},index(new int[]{30,50,70,100},cfg.dockOpacity),i->save(Config.DOCK_OPACITY_KEY,new int[]{30,50,70,100}[i])));
            row("焦点动画",cfg.animations?"开启":"关闭",()->save("animations",!cfg.animations));
            row("减少动态效果",cfg.reducedMotion?"开启":"关闭",()->save("reduced_motion",!cfg.reducedMotion));
            break;
        case 1:
            subtitle.setText("保留常用应用，让主屏幕更简洁");
            row("整理应用","移动与排序",()->{setResult(RESULT_OK,new Intent().putExtra(EXTRA_REORDER,true));finish();});
            row("显示更多应用行",String.valueOf(cfg.rowsBelowDock),()->choose("常用栏下的应用行数",new String[]{"0 行","1 行","2 行","3 行"},cfg.rowsBelowDock,i->save(Config.ROWS_BELOW_DOCK_KEY,i)));
            row("开机后打开",startLabel(),this::startPicker);
            row("默认桌面","选择桌面",()->open(new Intent(this,HomeSettingsActivity.class)));
            row("恢复桌面外观","恢复此版本默认设置",()->choose("恢复桌面外观？",new String[]{"取消","恢复默认外观"},0,i->{if(i==1){for(String key:new String[]{"columns",Config.ROWS_BELOW_DOCK_KEY,"animations","advanced_effects","reduced_motion","scene_off",Config.DOCK_OPACITY_KEY,Config.CLOCK_FORMAT_KEY,"home_idle_clock_minutes"})prefs.edit().remove(key).apply();changed();showSection(section);}}));
            break;
        case 2:
            subtitle.setText("只在主屏幕闲置时启动，不打断正在播放的内容");
            int[] minutes={0,5,10,15,30}; String[] delays={"不自动启动","5 分钟","10 分钟","15 分钟","30 分钟"};
            row("等待时间",delays[index(minutes,cfg.homeIdleClockMinutes)],()->choose("等待时间",delays,index(minutes,cfg.homeIdleClockMinutes),i->save("home_idle_clock_minutes",minutes[i])));
            row("时钟格式","12".equals(cfg.clockFormat)?"12 小时":"24".equals(cfg.clockFormat)?"24 小时":"跟随系统",()->choose("时钟格式",new String[]{"跟随系统","12 小时","24 小时"},java.util.Arrays.asList("system","12","24").indexOf(cfg.clockFormat),i->save(Config.CLOCK_FORMAT_KEY,new String[]{"system","12","24"}[i])));
            row("立即预览","屏幕保护",()->open(new Intent(this,ClockActivity.class)));
            break;
        case 3:
            subtitle.setText("打开电视原生设置，调整连接、画质和声音");
            row("网络","无线网络与有线连接",()->open(SystemSettings.intent(this,Settings.ACTION_WIFI_SETTINGS)));
            row("画面","亮度、色彩与画质",()->open(SystemSettings.displayIntent(this)));
            row("声音与设备","音频输出与遥控器",()->open(SystemSettings.intent(this,Settings.ACTION_SETTINGS)));
            row("应用管理","已安装的应用",()->open(SystemSettings.intent(this,Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
            row("小米电视设置","全部系统设置",()->{Intent v=SystemSettings.vendorIntent(this);open(v!=null?v:SystemSettings.intent(this,Settings.ACTION_SETTINGS));});
            break;
        default:
            subtitle.setText("CleanYourTV · 客厅定制版");
            paragraph("更专注于内容的电视桌面。\n\n基于 CleanYourTV 开源项目，按 tvOS 的空间层次、焦点反馈和遥控器交互重新设计。\n\n版本 3.4-tv.2\n基于 MIT 许可。壁纸沿用原项目的自由许可素材。\n\n这是 Android 电视桌面，不包含 Apple 的系统服务。");
        }
    }
    private static int index(int[] values,int v){for(int i=0;i<values.length;i++)if(values[i]==v)return i;return 0;}
    private void paragraph(String text){TextView t=TvStyle.text(this,text,17,TvStyle.SECONDARY);t.setLineSpacing(d(5),1);detail.addView(t);}
    private void row(String title,String value,Runnable action){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true);
        row.setBackgroundResource(R.drawable.tv_settings_row); row.setPadding(d(20),0,d(20),0);
        TextView name=TvStyle.text(this,title,17,TvStyle.INK); name.setDuplicateParentStateEnabled(true);
        name.setTextColor(TvStyle.ink(TvStyle.INK));
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=TvStyle.text(this,value,15,TvStyle.SECONDARY); status.setDuplicateParentStateEnabled(true);
        status.setTextColor(TvStyle.ink(TvStyle.SECONDARY));status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setMaxWidth(d(240)); status.setGravity(Gravity.RIGHT);row.addView(status,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(54));lp.bottomMargin=d(1);detail.addView(row,lp);
        row.setOnClickListener(v->action.run());
        row.setOnKeyListener((v,key,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_LEFT){navigation.getChildAt(section).requestFocus();return true;}return false;});
    }

    private void changed(){setResult(RESULT_OK);prefs.edit().putLong(Config.SETTINGS_REVISION_KEY,prefs.getLong(Config.SETTINGS_REVISION_KEY,0)+1).apply();}
    private void save(String key,Object value){
        int at=detail.indexOfChild(getCurrentFocus());
        SharedPreferences.Editor e=prefs.edit();if(value instanceof Integer)e.putInt(key,(Integer)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);else e.putString(key,String.valueOf(value));e.apply();changed();showSection(section);
        if(at>=0&&at<detail.getChildCount())detail.getChildAt(at).requestFocus();
    }
    private void choose(String title,String[] labels,int selected,java.util.function.IntConsumer action){
        View origin=getCurrentFocus(); Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(d(26),d(26),d(26),d(26));panel.setBackground(new FrostedSurface(panel,FrostedSurface.capture(getWindow().getDecorView()),26,false));panel.setPadding(d(26),d(26),d(26),d(26));
        TextView h=TvStyle.text(this,title,26,TvStyle.INK);h.setPadding(d(4),0,0,d(24));panel.addView(h);
        ScrollView sc=new ScrollView(this);sc.setVerticalScrollBarEnabled(false);LinearLayout opts=new LinearLayout(this);opts.setOrientation(LinearLayout.VERTICAL);sc.addView(opts);panel.addView(sc);
        for(int i=0;i<labels.length;i++){final int k=i;TextView t=TvStyle.text(this,labels[i]+(i==selected?"   ✓":""),18,TvStyle.INK);TvStyle.focusText(t);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(d(18),0,d(18),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(50));lp.bottomMargin=d(5);opts.addView(t,lp);t.setOnClickListener(v->{dialog.dismiss();action.accept(k);});}
        dialog.setContentView(panel); Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.6f);w.setLayout(d(440),Math.min(d(450),d(110+labels.length*55)));dialog.setOnDismissListener(v->{if(origin!=null&&origin.isAttachedToWindow())origin.requestFocus();});dialog.show();w.setLayout(d(440),Math.min(d(450),d(110+labels.length*55)));opts.getChildAt(Math.max(0,Math.min(labels.length-1,selected))).requestFocus();
    }
    private String startLabel(){String p=prefs.getString(OpenOnStart.KEY,"");if(p.isEmpty())return "主屏幕";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString();}catch(Exception e){return "主屏幕";}}
    private void startPicker(){List<String> names=new ArrayList<>(),packages=new ArrayList<>();names.add("主屏幕");packages.add("");for(android.content.pm.ResolveInfo r:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0)){String p=r.activityInfo.packageName;if(!p.equals(getPackageName())&&!packages.contains(p)&&!p.toLowerCase().contains("factory")){names.add(r.loadLabel(getPackageManager()).toString());packages.add(p);}}choose("开机后打开",names.toArray(new String[0]),packages.indexOf(prefs.getString(OpenOnStart.KEY,"")),i->{OpenOnStart.skipThisBoot(this);save(OpenOnStart.KEY,packages.get(i));});}
    private void open(Intent intent){try{startActivity(intent);}catch(android.content.ActivityNotFoundException|SecurityException e){Toast.makeText(this,"此设置暂不可用",Toast.LENGTH_SHORT).show();}}
    @Override public void onBackPressed(){if(detail.hasFocus()){navigation.getChildAt(section).requestFocus();return;}super.onBackPressed();}
}
