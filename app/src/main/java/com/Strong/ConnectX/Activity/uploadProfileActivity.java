package com.Strong.ConnectX.Activity;

import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.createChooser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.Strong.ConnectX.Utilities.Constants;
import com.Strong.ConnectX.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class uploadProfileActivity extends AppCompatActivity {

    ActivityProfileBinding BindProfile;
    private Uri filePath;
    StorageReference storageReference;
    private FirebaseAuth mAuth;
    String username, email, pass, id;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();
        super.onCreate(savedInstanceState);
        BindProfile = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(BindProfile.getRoot());

        // Retrieve data from intent
        username = getIntent().getStringExtra(Constants.KEY_USERNAME);
        email = getIntent().getStringExtra(Constants.KEY_EMAIL);
        pass = getIntent().getStringExtra(Constants.KEY_PASSWORD);
        id = getIntent().getStringExtra(Constants.KEY_ID);

        // Initialize ActivityResultLauncher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        filePath = result.getData().getData();
                        try {
                            BindProfile.pickImage.setVisibility(View.INVISIBLE);
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                            BindProfile.newProfileImage.setImageBitmap(bitmap);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        // Image selection
        BindProfile.newProfileImage.setOnClickListener(view -> SelectImage());

        // Upload button
        BindProfile.uploadProfile.setOnClickListener(view -> {
            try {
                uploadImage();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void SelectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(ACTION_GET_CONTENT);
        imagePickerLauncher.launch(createChooser(intent, "Select Image From Here"));
    }

    private void uploadImage() throws IOException {
        if (filePath != null) {
            showToast("Uploading Profile Pic");
            visibility(true);

            FirebaseDatabase database = FirebaseDatabase.getInstance();
            storageReference = FirebaseStorage.getInstance().getReference();

            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 50, bas);
            byte[] data = bas.toByteArray();

            mAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String id = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
                    storageReference = storageReference.child("ProfileImages/" + id);

                    storageReference.putBytes(data).addOnSuccessListener(taskSnapshot -> {
                        storageReference.getDownloadUrl().addOnSuccessListener(uri -> {
                            HashMap<String, Object> hashMap = new HashMap<>();
                            hashMap.put(Constants.KEY_USERNAME, username);
                            hashMap.put(Constants.KEY_EMAIL, email);
                            hashMap.put(Constants.KEY_PASSWORD, pass);
                            hashMap.put(Constants.CHAT_USER_IMAGE, uri.toString());
                            hashMap.put(Constants.KEY_ID, id);
                            database.getReference().child("Users").child(id).setValue(hashMap);
                            showToast("Image Uploaded!");
                        });

                        showToast("Welcome " + username + " PersonalChat.");
                        Intent intent = new Intent(uploadProfileActivity.this, recentActivity.class);
                        startActivity(intent);
                    }).addOnFailureListener(e -> {
                        visibility(false);
                        showToast("Failed: " + e.getMessage());
                    });
                } else {
                    showToast(Objects.requireNonNull(task.getException()).getMessage());
                }
            });
        } else {
            showToast("Please select an image first.");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void visibility(boolean key) {
        if (key) {
            BindProfile.profileProgress.setVisibility(View.VISIBLE);
            BindProfile.uploadProfile.setVisibility(View.INVISIBLE);
        } else {
            BindProfile.profileProgress.setVisibility(View.INVISIBLE);
            BindProfile.uploadProfile.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
