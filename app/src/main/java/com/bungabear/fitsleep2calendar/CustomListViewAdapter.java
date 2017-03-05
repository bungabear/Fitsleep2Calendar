package com.bungabear.fitsleep2calendar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Minjae on 2017-02-27.
 * 커스팀 리스트뷰 어댑터. 커스텀 리스트 데이터를 배열로 담아 관리한다.
 */

class CustomListViewAdapter extends BaseAdapter{
    private Context mContext = null;
    private ArrayList<CustomListData> mListData = new ArrayList<>();

    CustomListViewAdapter(Context context){
        mContext = context;
    }

    public void addItem(String id, String startTime, String endTime, boolean isExclude, String event_ID){
        if(event_ID == null) event_ID = "x";
        CustomListData addInfo = new CustomListData(id, startTime, endTime,isExclude, event_ID);

        mListData.add(addInfo);
    }

    public void clear(){
        mListData.clear();
    }

    @Override
    public int getCount() {
        return mListData.size();
    }

    @Override
    public CustomListData getItem(int position) {
        return mListData.get(position);
    }

    public ArrayList<CustomListData> getListData(){
        return mListData;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    //아이템에 들어갈 레이아웃을 지정
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        CustomViewHolder holder;
        //화면에 캐쉬가 없으면 새로 생성해줌
        if(convertView == null){
            holder = new CustomViewHolder();

            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.custom_list_item, null);

            holder.id = (TextView) convertView.findViewById(R.id.id);
            holder.startTime = (TextView) convertView.findViewById(R.id.startTime);
            holder.endTime = (TextView) convertView.findViewById(R.id.endTime);
            holder.isExclude = (CheckBox) convertView.findViewById(R.id.isExclude);
            holder.event_ID = (TextView) convertView.findViewById(R.id.event_ID);

            convertView.setTag(holder);
        } else {
            //있으면 태그에 저장했던 홀더를 불러옴
            holder = (CustomViewHolder) convertView.getTag();
        }
        CustomListData listData = mListData.get(position);
        holder.id.setText(listData.id);
        holder.startTime.setText(listData.startTime);
        holder.endTime.setText(listData.endTime);
        holder.isExclude.setChecked(!(listData.isExclude));
        holder.event_ID.setText(listData.event_ID);
        holder.isExclude.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mListData.get(position).isExclude = !isChecked;
            }
        });

        return convertView;
    }
    //ListView Item 뷰를 담는 클래스
    private class CustomViewHolder {
        private TextView id ;
        private TextView startTime ;
        private TextView endTime ;
        private CheckBox isExclude ;
        private TextView event_ID ;

    }
    //ListView Item 객체들이 가질 데이터를 담는 클래스
    public class CustomListData {
        private String id;
        private String startTime;
        private String endTime;
        private String event_ID;
        private boolean isExclude;

        CustomListData(String i, String sT, String eT, boolean iE, String eID){
            id = i;
            startTime = sT;
            endTime = eT;
            isExclude = iE;
            event_ID = eID;
        }

        public String getStartTime(){
            return startTime;
        }
        public String getEndTime(){
            return endTime;
        }
        public String getFormattedStartTime(){
            return event_ID.split(" ~ ")[0] + "T" + startTime.substring(6) + ":00+09:00";
        }
        public String getFormattedEndTime(){
            return event_ID.split(" ~ ")[1] + "T" + endTime.substring(6) + ":00+09:00";
        }
        public String getEvent_ID(){
            return event_ID;
        }
        public String getID(){
            return id;
        }
        public boolean getisExclude(){
            return isExclude;
        }
    }

}

