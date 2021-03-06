package com.killerwhale.memary.Activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.killerwhale.memary.Helper.PermissionHelper;
import com.killerwhale.memary.R;
import com.killerwhale.memary.View.EditUsernameDialog;

import java.util.Objects;

public class ProfileActivity extends AppCompatActivity implements EditUsernameDialog.EditUsernameDialogListener {

    private static final String TAG = "PROFILE";
    private static final int PICK_FROM_GALLERY = 9999;

    // UI widgets
    private BottomNavigationView navBar;
    private SimpleDraweeView icUserInfoAvatar;
    private ImageButton btnCamera;
    private ImageButton btnWrite;
    private TextView txtName;
    private Button btnSetting;
    private Button btnMyPosts;
    private Button btnLogout;
    private SimpleDraweeView arIcon;

    // Database
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private StorageReference storageRef;
    private String Uid;

    @Override
    protected void onStart() {
        super.onStart();

        // if signed in, get Firebase Auth Uid, else do something
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser == null){
//            startActivity(new Intent(getBaseContext(), SignInActivity.class));
        } else {
            Uid = currentUser.getUid();
            if(db != null) {
                final DocumentReference docRef = db.collection("users").document(Uid);
                docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document != null) {
                                String avatarString = (String) document.get("avatar");
                                if (avatarString != null) {
                                    icUserInfoAvatar.setImageURI(Uri.parse(avatarString));
                                }
                                String usernameString = (String) document.get("username");
                                if (usernameString != null) {
                                    txtName.setText(usernameString);
                                }
                            } else {
                                Log.d(TAG, "error getting user profile");
                            }
                        } else {
                            Log.d(TAG, "get failed with ", task.getException());
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Database
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference().child("avatars");

        // UI init
        navBar = findViewById(R.id.navBar);
        arIcon = findViewById(R.id.bigIcon);
        icUserInfoAvatar = (SimpleDraweeView) findViewById(R.id.icUserInfoAvatar);
        btnCamera = (ImageButton) findViewById(R.id.btnCamera);

        txtName = (TextView) findViewById(R.id.txtName);
        btnSetting = (Button) findViewById(R.id.btnSetting);
        btnWrite = (ImageButton) findViewById(R.id.btnWrite);
        btnMyPosts = (Button) findViewById(R.id.btnMyPosts);
        btnLogout = (Button) findViewById(R.id.btnLogout);

        // UI listeners
        navBar.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_map:
                        startActivity(new Intent(getBaseContext(), MapActivity.class));
                        finish();
                        break;
                    case R.id.action_posts:
                        startActivity(new Intent(getBaseContext(), PostFeedActivity.class));
                        finish();
                        break;
                    case R.id.action_places:
                        startActivity(new Intent(getBaseContext(), LocationListActivity.class));
                        finish();
                        break;
                    case R.id.action_profile:
                        break;
                    default:
                        Log.i(TAG, "Unhandled nav click");

                }
                return true;
            }
        });

        arIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!PermissionHelper.hasPermissions(getBaseContext(), PermissionHelper.PERMISSIONS_AR)) {
                    ActivityCompat.requestPermissions(ProfileActivity.this,
                            PermissionHelper.PERMISSIONS_AR,
                            PermissionHelper.PERMISSION_CODE_AR);
                } else {
                    Intent i = new Intent(getBaseContext(), ARActivity.class);
                    startActivity(i);
                }
            }
        });

        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // if gallery permission not granted, request permission, else go to gallery
                if (!PermissionHelper.hasPermissions(getBaseContext(), PermissionHelper.PERMISSION_PROFILE)) {
                    ActivityCompat.requestPermissions(ProfileActivity.this,
                            PermissionHelper.PERMISSION_PROFILE,
                            PermissionHelper.PERMISSION_CODE_PROFILE);
                } else {
                    Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(galleryIntent, PICK_FROM_GALLERY);
                }
            }
        });

        // change username
        btnWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDialog();
            }
        });

        // show all user's posts
        btnMyPosts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getBaseContext(), MyPostsActivity.class));
            }
        });

        btnSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getBaseContext(), SettingActivity.class));
            }
        });

        //logout and return to SignInActivity
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
                Toast.makeText(getBaseContext(), "signed out successfully", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(getBaseContext(), SignInActivity.class);
                startActivity(intent);
            }
        });
    }

    private void uploadAvatar(Uri uri){
        final StorageReference avatarImgRef = storageRef.child(Uid + ".jpg");
        UploadTask uploadTask = avatarImgRef.putFile(uri);

        //upload the image to firebase storage, uid as name, if exists a image with same name, replace it
        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw Objects.requireNonNull(task.getException());
                }
                // Continue with the task to get the download URL
                return avatarImgRef.getDownloadUrl();
            }
        });
        urlTask.addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    if (downloadUri != null) {
                        updateUsersAvatar(downloadUri.toString());
                    }
                } else {
                    // Handle failures
                    Log.e(TAG, "Upload failed.");
                }
            }
        });
    }

    //update user table's avatar field
    private void updateUsersAvatar(String remoteUrl){
        if(db != null) {
            DocumentReference user = db.collection("users").document(Uid);
            user.update("avatar", remoteUrl);
        }
        icUserInfoAvatar.setImageURI(remoteUrl);
    }

    //update user table's username field
    private void updateUsersUsername(String username){
        if(db != null) {
            DocumentReference user = db.collection("users").document(Uid);
            user.update("username", username);
        }
        txtName.setText(username);
    }

    private void openDialog(){
        EditUsernameDialog editUsernameDialog = new EditUsernameDialog();
        editUsernameDialog.show(getSupportFragmentManager(), "edit username dialog");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //if chosen a image, update the view with new image
        if (requestCode == PICK_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            uploadAvatar(uri);
        } else{
            Toast.makeText(getBaseContext(), "There was an error when fetching image", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PermissionHelper.PERMISSION_CODE_AR) {
            if (PermissionHelper.hasGrantedAll(grantResults)) {
                Intent i = new Intent(getBaseContext(), ARActivity.class);
                startActivity(i);
            }
        } else if (requestCode == PermissionHelper.PERMISSION_CODE_PROFILE) {
            if (PermissionHelper.hasGrantedAll(grantResults)) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, PICK_FROM_GALLERY);
            }
        }
    }

    //override method from dialog fragment
    @Override
    public void sendUsername(String username) {
        updateUsersUsername(username);
    }

    //set the navBar view
    @Override
    protected void onResume() {
        super.onResume();
        navBar.setSelectedItemId(R.id.action_profile);

    }
}
