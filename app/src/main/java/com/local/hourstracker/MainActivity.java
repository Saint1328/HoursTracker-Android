package com.local.hourstracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int GPS_PERMISSION=11, EXPORT_CSV=12;
    private final String[] places={"Site","Accommodation","RB office","JHB office"};
    private final String[] functions={"Audits","Deployments","Processing"};
    private Spinner placeSpinner, functionSpinner;
    private TextView status, summary, history;
    private LinearLayout root;
    private boolean dark;
    private int savedPlace=0, savedFunction=0;
    private JSONArray entries;
    private JSONObject active;
    private String pendingGpsFor="";
    private final SimpleDateFormat dayFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
    private final SimpleDateFormat displayTime=new SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault());
    private final SimpleDateFormat fileDate=new SimpleDateFormat("yyyyMMdd",Locale.US);

    @Override public void onCreate(Bundle saved){super.onCreate(saved); load(); draw();}
    private void load(){
        android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);
        dark=p.getBoolean("dark",true);
        savedPlace=p.getInt("savedPlace",0);
        savedFunction=p.getInt("savedFunction",0);
        try { entries=new JSONArray(p.getString("entries","[]")); } catch(Exception e){ entries=new JSONArray(); }
        try { active=new JSONObject(p.getString("active","")); } catch(Exception e){active=null;}
    }
    private void persist(){getPreferences(MODE_PRIVATE).edit().putString("entries",entries.toString()).putString("active",active==null?"":active.toString()).putBoolean("dark",dark).putInt("savedPlace",savedPlace).putInt("savedFunction",savedFunction).apply();}
    private int bg(){return dark?Color.rgb(17,24,39):Color.rgb(248,250,252);}
    private int fg(){return dark?Color.WHITE:Color.rgb(20,30,47);}
    private int muted(){return dark?0xffcbd5e1:0xff475569;}
    private TextView label(String s,int size){ TextView t=new TextView(this);t.setText(s);t.setTextColor(fg());t.setTextSize(size);t.setPadding(0,12,0,10);return t; }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private void draw(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(22,18,22,24);root.setBackgroundColor(bg());
        ScrollView sc=new ScrollView(this);sc.addView(root);setContentView(sc);
        TextView title=label("HOURS TRACKER",25);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        Button theme=button(dark?"Switch to light mode":"Switch to dark mode");root.addView(theme);theme.setOnClickListener(v->{dark=!dark;persist();draw();});
        root.addView(label("Location",15));placeSpinner=new Spinner(this);placeSpinner.setAdapter(adapter(places));root.addView(placeSpinner);
        root.addView(label("Function",15));functionSpinner=new Spinner(this);functionSpinner.setAdapter(adapter(functions));root.addView(functionSpinner);
        placeSpinner.setSelection(active!=null?find(places,active.optString("place")):Math.min(savedPlace,places.length-1));
        functionSpinner.setSelection(active!=null?find(functions,active.optString("function")):Math.min(savedFunction,functions.length-1));
        placeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){savedPlace=position;persist();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        functionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){savedFunction=position;persist();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        status=label("",16);root.addView(status);
        Button in=button("CLOCK IN - capture time & GPS");root.addView(in);in.setOnClickListener(v->clockIn());
        Button out=button("CLOCK OUT - capture time & GPS");root.addView(out);out.setOnClickListener(v->clockOut());
        summary=label("",17);root.addView(summary);
        Button export=button("EXPORT CSV TO PHONE / PC");root.addView(export);export.setOnClickListener(v->export());
        history=label("",13);root.addView(history);
        root.addView(label("Standard: 07:30-16:30 | 60 min unpaid break | 8 net hours/day. GPS may be unavailable indoors; time recording still works.",12));
        refresh();
    }
    private ArrayAdapter<String> adapter(String[] a){ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,a);ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return ad;}
    private int find(String[] arr,String val){for(int i=0;i<arr.length;i++)if(arr[i].equals(val))return i;return 0;}
    private void clockIn(){
        if(active!=null){toast("Already clocked in. Clock out first.");return;}
        active=new JSONObject();try {
            active.put("in",System.currentTimeMillis());active.put("place",places[placeSpinner.getSelectedItemPosition()]);
            active.put("function",functions[functionSpinner.getSelectedItemPosition()]);active.put("inGps","Pending");
        }catch(Exception ignored){}
        persist();refresh();gps("inGps");
    }
    private void clockOut(){
        if(active==null){toast("Clock in first.");return;}
        JSONObject record=active;
        try {record.put("out",System.currentTimeMillis());record.put("outGps","Pending");entries.put(record);}catch(Exception ignored){}
        active=null;persist();refresh();gps("outGps");
    }
    private void gps(String field){
        pendingGpsFor=field;
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPS_PERMISSION);return;
        }
        obtainGps(field);
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] results){super.onRequestPermissionsResult(request,perms,results);if(request==GPS_PERMISSION)obtainGps(pendingGpsFor);}
    private void obtainGps(String field){
        JSONObject target=field.equals("inGps")?active:(entries.length()>0?entries.optJSONObject(entries.length()-1):null);
        if(target==null)return;
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){saveGps(target,field,"Permission denied");return;}
        Location location=null;
        try {location=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
        if(location==null)try {location=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        if(location!=null && System.currentTimeMillis()-location.getTime()<=120000){saveGps(target,field,locString(location));return;}
        try {
            String provider=lm.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            lm.requestSingleUpdate(provider,new android.location.LocationListener(){@Override public void onLocationChanged(Location loc){saveGps(target,field,locString(loc));}},null);
            saveGps(target,field,"Waiting for GPS fix");
        }catch(Exception ex){saveGps(target,field,"GPS unavailable");}
    }
    private String locString(Location l){return String.format(Locale.US,"%.6f,%.6f (accuracy %.0fm)",l.getLatitude(),l.getLongitude(),l.getAccuracy());}
    private void saveGps(JSONObject target,String field,String value){try{target.put(field,value);}catch(Exception ignored){}persist();refresh();}
    private String fmt(long time){return displayTime.format(new Date(time));}
    private long netMinutes(JSONObject e){return Math.max(0,(e.optLong("out")-e.optLong("in"))/60000-60);}
    private void refresh(){
        if(status==null)return;
        status.setText(active==null?"Status: Clocked out":"CLOCKED IN since "+fmt(active.optLong("in"))+"\nGPS: "+active.optString("inGps"));
        long month=0,week=0,ot=0;
        Calendar now=Calendar.getInstance(),cal=Calendar.getInstance();
        now.setFirstDayOfWeek(Calendar.MONDAY);
        StringBuilder log=new StringBuilder("\nRECENT SHIFTS\n");
        for(int i=entries.length()-1;i>=0;i--){JSONObject e=entries.optJSONObject(i);if(e==null)continue;cal.setTimeInMillis(e.optLong("in"));long minutes=netMinutes(e);
            if(cal.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&cal.get(Calendar.MONTH)==now.get(Calendar.MONTH)){month+=minutes;ot+=Math.max(0,minutes-480);}
            if(cal.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&cal.get(Calendar.WEEK_OF_YEAR)==now.get(Calendar.WEEK_OF_YEAR))week+=minutes;
            if(i>=entries.length()-12)log.append("\n").append(fmt(e.optLong("in"))).append(" - ").append(fmt(e.optLong("out"))).append("\n").append(e.optString("place")).append(" / ").append(e.optString("function")).append(" | ").append(hours(minutes)).append(" worked | ").append(hours(Math.max(0,minutes-480))).append(" OT\n");
        }
        summary.setText("THIS WEEK: "+hours(week)+"\nTHIS MONTH: "+hours(month)+"\nMONTHLY OVERTIME: "+hours(ot)+"\nTOTAL SHIFTS: "+entries.length());
        history.setText(log.toString());
    }
    private String hours(long min){return String.format(Locale.US,"%dh %02dm",min/60,min%60);}
    private void export(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,"hours-"+fileDate.format(new Date())+".csv");startActivityForResult(i,EXPORT_CSV);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==EXPORT_CSV&&result==RESULT_OK&&data!=null){
        try(OutputStream stream=getContentResolver().openOutputStream(data.getData());OutputStreamWriter w=new OutputStreamWriter(stream,"UTF-8")){
            w.write("Date,Clock In,Clock Out,Location,Function,Break Minutes,Net Hours,Overtime Hours,Clock In GPS,Clock Out GPS\n");
            for(int j=0;j<entries.length();j++){JSONObject e=entries.optJSONObject(j);if(e==null)continue;long net=netMinutes(e);
                String[] cols={dayFormat.format(new Date(e.optLong("in"))),fmt(e.optLong("in")),fmt(e.optLong("out")),e.optString("place"),e.optString("function"),"60",String.format(Locale.US,"%.2f",net/60.0),String.format(Locale.US,"%.2f",Math.max(0,net-480)/60.0),e.optString("inGps"),e.optString("outGps")};
                for(int k=0;k<cols.length;k++){if(k>0)w.write(',');w.write('"'+cols[k].replace("\"","\"\"")+'"');}w.write("\n");
            }
            toast("CSV saved");
        }catch(Exception ex){toast("Export failed: "+ex.getMessage());}
    }}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
