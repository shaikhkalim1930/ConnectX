package com.Strong.ConnectX.Activity;

import static android.content.Intent.ACTION_PICK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.Strong.ConnectX.Adaptors.messageAdaptor;
import com.Strong.ConnectX.R;
import com.Strong.ConnectX.Utilities.APIService;
import com.Strong.ConnectX.Utilities.Client;
import com.Strong.ConnectX.Utilities.Constants;
import com.Strong.ConnectX.Utilities.Data;
import com.Strong.ConnectX.Utilities.MyResponse;
import com.Strong.ConnectX.Utilities.NotificationSender;
import com.Strong.ConnectX.databinding.ActivityMainChatBinding;
import com.Strong.ConnectX.models.message;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class mainChatActivity extends AppCompatActivity {
    static int seen;
    private final int REQ_IMAGE = 500;
    FirebaseAuth fAuth;
    private static String MineId;
    private static String YourID;
    FirebaseDatabase database;
    DatabaseReference reference;
    StorageReference StoreRef;
    ActivityMainChatBinding BindMainChat;
    private APIService apiService;
    private static String Token;
    private static String YourName, YourImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BindMainChat = ActivityMainChatBinding.inflate(getLayoutInflater());
        setContentView(BindMainChat.getRoot());

        fAuth = FirebaseAuth.getInstance();
        apiService = Client.getClient("https://fcm.googleapis.com/").create(APIService.class);

        MineId = fAuth.getUid();
        YourName = getIntent().getStringExtra("username");
        YourImage = getIntent().getStringExtra("UserImage");
        YourID = getIntent().getStringExtra("userId");
        if (YourName == null) {
            YourName = getIntent().getStringExtra("RecName");
            YourImage = getIntent().getStringExtra("RecImage");
            YourID = getIntent().getStringExtra("RecID");
        }

        BindMainChat.mainChatUsername.setText(YourName);
        Glide.with(this).load(YourImage).into(BindMainChat.mainChatImage);
        BindMainChat.TypeMessage.requestFocus();

        final ArrayList<message> messageModels = new ArrayList<>();
        final messageAdaptor messageAdaptor = new messageAdaptor(messageModels, this);
        database = FirebaseDatabase.getInstance();

        database.getReference("Users").child(YourID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Token = snapshot.child("Token").getValue(String.class);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        showMessage(messageAdaptor, messageModels, messageModels.size());

        database.getReference().child("Users").child(MineId).child("ChatRoom").child(YourID)
                .addValueEventListener(new ValueEventListener() {
                    @SuppressLint("SetTextI18n")
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            String key = dataSnapshot.getKey();
                            String value = dataSnapshot.getValue(String.class);
                            if ("chatRoom".equals(key)) {
                                seen = "1".equals(value) ? 1 : 0;
                                BindMainChat.ActiveStatus.setText(seen == 1 ? "Active Now" : null);
                            } else if ("Typing".equals(key)) {
                                boolean isTyping = value != null && value.length() != 0;
                                BindMainChat.TypingStatus.setVisibility(isTyping ? View.VISIBLE : View.GONE);
                                BindMainChat.ActiveStatus.setVisibility(isTyping ? View.GONE : View.VISIBLE);
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        initSendMessage();
        initTyping();

        BindMainChat.mainchatbackButton.setOnClickListener(v -> {
            BindMainChat.TypeMessage.setText(null);
            setRoom("0");
            onBackPressed();
        });

        BindMainChat.constraint.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserDataShow.class);
            intent.putExtra("Image", YourImage);
            intent.putExtra("username", YourName);
            startActivity(intent);
        });

        initOption();

        BindMainChat.videCallButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, VideoCallOutgoing.class);
            intent.putExtra("Uid", YourID);
            intent.putExtra("OutName", YourName);
            intent.putExtra("OutImage", YourImage);
            startActivity(intent);
        });

        BindMainChat.chooseImage.setOnClickListener(view -> {
            checkAndRequestImagePermission();
        });

        BindMainChat.swipeRefresh.setOnRefreshListener(() -> {
            showMessage(messageAdaptor, messageModels, messageModels.size());
            BindMainChat.swipeRefresh.setRefreshing(false);
        });

        database.getReference().keepSynced(true);
    }

    private void checkAndRequestImagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_IMAGE);
            } else launchImagePicker();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_IMAGE);
            } else launchImagePicker();
        }
    }

    private void launchImagePicker() {
        Intent intent = new Intent(ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        FileChooser.launch(Intent.createChooser(intent, "Select Image To Send"));
    }

    private final ActivityResultLauncher<Intent> FileChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri filePath = result.getData().getData();
                    if (filePath == null) return;

                    BindMainChat.progressUpload.setVisibility(View.VISIBLE);
                    BindMainChat.chooseImage.setVisibility(View.INVISIBLE);
                    StoreRef = FirebaseStorage.getInstance().getReference()
                            .child("Media").child("ImagePics")
                            .child(MineId).child(YourID)
                            .child(String.valueOf(System.currentTimeMillis()));

                    try {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                        ByteArrayOutputStream bas = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 50, bas);
                        byte[] byteData = bas.toByteArray();

                        StoreRef.putBytes(byteData).addOnSuccessListener(success -> {
                            Task<Uri> urlTask = success.getStorage().getDownloadUrl();
                            urlTask.addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    String url = Objects.requireNonNull(task.getResult()).toString();
                                    message conversation = new message(MineId, url, "ImagePics");
                                    conversation.setTimeStamp(new Date().getTime());
                                    conversation.setSeen(seen == 1 ? "yes" : "no");
                                    if (seen == 0) sendNotification(Token, "📷 Image");

                                    database.getReference().child("Users").child(MineId)
                                            .child("Chats").child(YourID).push().setValue(conversation)
                                            .addOnSuccessListener(e -> database.getReference()
                                                    .child("Users").child(YourID)
                                                    .child("Chats").child(MineId)
                                                    .push().setValue(conversation));

                                    BindMainChat.progressUpload.setVisibility(View.INVISIBLE);
                                    BindMainChat.chooseImage.setVisibility(View.VISIBLE);
                                }
                            });
                        });

                    } catch (IOException e) {
                        BindMainChat.progressUpload.setVisibility(View.INVISIBLE);
                        BindMainChat.chooseImage.setVisibility(View.VISIBLE);
                        Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    public void sendNotification(String userToken, String YourMessage) {
        SharedPreferences shared = getSharedPreferences("ConnectX", MODE_PRIVATE);
        String MineName = shared.getString(Constants.KEY_USERNAME, "");
        String MineID = shared.getString(Constants.KEY_ID, "");
        String MineImage = shared.getString(Constants.CHAT_USER_IMAGE, "");
        Data data = new Data(MineName, YourMessage, MineImage, MineID);
        NotificationSender sender = new NotificationSender(data, userToken);
        apiService.sendNotification(sender).enqueue(new Callback<MyResponse>() {
            @Override public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
                if (response.code() == 200 && response.body() != null && response.body().success != 1)
                    Toast.makeText(mainChatActivity.this, "Error Sending Notification", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable t) {
                Toast.makeText(mainChatActivity.this, t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setRoom(String value) {
        reference = FirebaseDatabase.getInstance().getReference()
                .child("Users").child(YourID)
                .child("ChatRoom").child(MineId);
        HashMap<String, Object> map = new HashMap<>();
        map.put("chatRoom", value);
        reference.updateChildren(map);
        reference.keepSynced(true);
    }

    private void showMessage(messageAdaptor messageAdaptor, ArrayList<message> messageModels, int count) {
        database.getReference().child("Users").child(MineId)
                .child("Chats").child(YourID)
                .addValueEventListener(new ValueEventListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        messageModels.clear();
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            message model = dataSnapshot.getValue(message.class);
                            messageModels.add(model);
                        }
                        messageAdaptor.notifyDataSetChanged();
                        BindMainChat.mainChatRecyclerView.setAdapter(messageAdaptor);
                        BindMainChat.mainChatRecyclerView.smoothScrollToPosition(messageModels.size() - 1);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(mainChatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                        messageAdaptor.notifyDataSetChanged();
                    }
                });
    }

    private void initTyping() {
        DatabaseReference refer = FirebaseDatabase.getInstance().getReference()
                .child("Users").child(YourID)
                .child("ChatRoom").child(MineId);
        HashMap<String, Object> hashmap = new HashMap<>();
        BindMainChat.TypeMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (charSequence.length() != 0) {
                    hashmap.put("Typing", charSequence.toString());
                    BindMainChat.chooseImage.setVisibility(View.INVISIBLE);
                    BindMainChat.sendButton.setVisibility(View.VISIBLE);
                } else {
                    hashmap.put("Typing", "");
                    BindMainChat.chooseImage.setVisibility(View.VISIBLE);
                    BindMainChat.sendButton.setVisibility(View.INVISIBLE);
                }
                refer.updateChildren(hashmap);
                refer.keepSynced(true);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @SuppressLint("NonConstantResourceId")
    private void initOption() {
        BindMainChat.optionButton.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(getApplicationContext(), view);
            MenuInflater inflater = popupMenu.getMenuInflater();
            inflater.inflate(R.menu.option, popupMenu.getMenu());
            popupMenu.show();
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.report) {
                    Toast.makeText(getApplicationContext(), "You Clicked on Report", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (menuItem.getItemId() == R.id.block) {
                    Toast.makeText(getApplicationContext(), "You Clicked on Block", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (menuItem.getItemId() == R.id.deleteChat) {
                    deleteChat();
                    return false;
                }
                return false;
            });
        });
    }

    private void initSendMessage() {
        BindMainChat.sendButton.setOnClickListener(view -> {
            String messageText = Objects.requireNonNull(BindMainChat.TypeMessage.getText()).toString().trim();
            if (messageText.equals("")) return;
            message conversation = new message(MineId, messageText);
            conversation.setTimeStamp(new Date().getTime());
            if (seen == 1) conversation.setSeen("yes");
            else {
                conversation.setSeen("no");
                sendNotification(Token, messageText);
            }
            BindMainChat.TypeMessage.setText(null);
            FirebaseDatabase.getInstance().getReference()
                    .child("Users").child(MineId).child("Chats").child(YourID)
                    .push().setValue(conversation)
                    .addOnSuccessListener(e -> database.getReference()
                            .child("Users").child(YourID).child("Chats").child(MineId)
                            .push().setValue(conversation))
                    .addOnFailureListener(e -> Toast.makeText(mainChatActivity.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    public void deleteChat() {
        StorageReference StoreRef = FirebaseStorage.getInstance().getReference()
                .child("/Media/ImagePics/3AbizhbGLuYwQ1sWE5Av6OBV3di2/DP8DPJnZeMXk0rNhe6ngqxmNT122");
        StoreRef.listAll().onSuccessTask(listResult -> {
            for (StorageReference item : listResult.getItems()) {
                item.delete().addOnSuccessListener(unused -> {
                    database.getReference().child("Users").child(MineId).child("Chats").child(YourID).removeValue();
                    Toast.makeText(this, "Chat Deleted Successfully", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                }).addOnFailureListener(e -> Toast.makeText(this, "Can't Delete this Chat. " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
            }
            return null;
        }).addOnFailureListener(e -> {
            if ("Continuation returned null".equals(e.getLocalizedMessage())) {
                database.getReference().child("Users").child(MineId).child("Chats").child(YourID).removeValue();
                Toast.makeText(this, "Chat Deleted Successfully", Toast.LENGTH_SHORT).show();
                onBackPressed();
            }
        });
    }

    @Override protected void onPause() {
        super.onPause();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference().child("Users").child(YourID)
                .child("ChatRoom").child(MineId);
        ref.updateChildren(new HashMap<String,Object>(){{ put("Typing",""); }});
        ref.keepSynced(true);
        setRoom("0");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        setRoom("0");
    }

    @Override protected void onResume() {
        super.onResume();
        setRoom("1");
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_IMAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchImagePicker();
            } else {
                Toast.makeText(this, "Permission Denied. Please Allow Permission By App Info", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
