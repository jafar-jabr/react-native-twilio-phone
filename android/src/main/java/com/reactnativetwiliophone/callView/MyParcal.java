package com.reactnativetwiliophone.callView;


import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.parceler.Parcel;

public class MyParcal implements Parcelable {
  public String id;
  public String name;


  /**
   * Constructs a MyParcal from values
   */
  public MyParcal (String id, String name) {
    this.id = id;
    this.name = name;
  }


  protected MyParcal(android.os.Parcel in) {
    id = in.readString();
    name = in.readString();
  }

  public static final Creator<MyParcal> CREATOR = new Creator<MyParcal>() {
    @Override
    public MyParcal createFromParcel(android.os.Parcel in) {
      return new MyParcal(in);
    }

    @Override
    public MyParcal[] newArray(int size) {
      return new MyParcal[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(name);
  }

}
