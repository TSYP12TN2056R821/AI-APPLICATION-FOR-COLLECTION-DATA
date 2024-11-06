package com.example.garbagedataclassificationcollection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.floatingactionbutton.FloatingActionButton;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements DataCommunicationInterface {
    private static final int REQUEST_WRITE_SOTRAGE = 1;
    private static final int REQUEST_READ_SOTRAGE = 2;

    private TextView pathTxt;
    private ActivityResultLauncher<Intent> documentTreeLauncher;
    private DocumentFile myDataSetDirectory;
    private DocumentFile myDataSet;
    private FloatingActionButton camBtn;
    private EditText garbageField;
    private EditText qteField;
    private boolean isDatasetLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pathTxt = findViewById(R.id.path_txt);
        camBtn = findViewById(R.id.cam_btn);
        garbageField= findViewById(R.id.garbage_field);
        qteField = findViewById(R.id.qte_field);
        qteField.setInputType(InputType.TYPE_NULL);
        findViewById(R.id.browse_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askForReadPermission();
            }
        });

        camBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // get the number of elements in the selected class in csv if exists
                if(!garbageField.getText().toString().trim().equals("") && isDatasetLoaded){

                    DocumentFile garbageClassFolder = createImageFolder(garbageField.getText().toString().trim());

                    Intent i = new Intent(MainActivity.this, CameraFeedActivity.class);

                    i.putExtra(GARBAGE_CLASS_FOLDER, garbageClassFolder.getUri());
                    i.putExtra(CSV_FILE, myDataSet.getUri());
                    i.putExtra(GARBAGE_CLASS_NAME, garbageClassFolder.getName());
                    i.putExtra(GARBAGE_CLASS_NUMBER, Integer.parseInt(qteField.getText().toString()));

                    // app returns to older state
                    garbageField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
                    isDatasetLoaded = false;
                    pathTxt.setText("");
                    qteField.setText("");

                    // start next activity
                    startActivity(i);
                }
            }
        });

        documentTreeLauncher =registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result->{
            if(result.getResultCode() == RESULT_OK){
                Intent data = result.getData();
                if(data!=null){
                    Uri treeUri = handleDirectory(data);
                    myDataSetDirectory= DocumentFile.fromTreeUri(this, treeUri);
                    // check if you can write in directory
                    // get CSV Document
                    boolean foundFile = csvFileExists();

                    if(!foundFile){
                        myDataSet = myDataSetDirectory.createFile("text/csv", "myDataSet.csv");
                        writeHeader(myDataSet);
                    }
                    pathTxt.setText(R.string.dir_loaded);
                    garbageField.setInputType(InputType.TYPE_NULL); // make sure to make it editable after returning from CameraFeedActivity
                    qteField.setText(""+getGarbageClassNbr(garbageField.getText().toString(), myDataSet));
                    isDatasetLoaded =true;
                }
            }
        });


    }

    private DocumentFile createImageFolder(String garbageClassName){
        DocumentFile garbageClassFolder = null;
        /* Todo: Check if the folder for selected garbage name exists, if not, create it!*/
        for(DocumentFile localGarbageClassFolder: myDataSetDirectory.listFiles()){
            if(localGarbageClassFolder.canWrite() && localGarbageClassFolder.isDirectory() && localGarbageClassFolder.getName().equals(garbageClassName)){
                garbageClassFolder = localGarbageClassFolder;
                break;
            }
        }
        if(garbageClassFolder==null){
            garbageClassFolder= myDataSetDirectory.createDirectory(garbageClassName);
        }
        return garbageClassFolder;
    }

    private boolean csvFileExists(){
        boolean foundCsv=false;

        DocumentFile[] files = myDataSetDirectory.listFiles();
        int i=0;
        while(!foundCsv && i<files.length){
            if(files[i].isFile() && files[i].getName()!=null && files[i].getName().endsWith(".csv")){
                // found it!!
                foundCsv=true;
                myDataSet = files[i];

            }
            i++;
        }
        Toast.makeText(this,"File found = " +foundCsv, Toast.LENGTH_SHORT).show();
        return foundCsv;
    }

    private void writeHeader(DocumentFile csvFile){
        if(myDataSet!=null && myDataSet.canWrite()){
            try{
                OutputStream out = getContentResolver().openOutputStream(myDataSet.getUri());
                String initData = "garbage_class, image";
                out.write(initData.getBytes());
                out.close();
                Toast.makeText(this, "Wrote File successfully", Toast.LENGTH_SHORT).show();
            }catch(IOException e){
                Toast.makeText(this, ""+e, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int getGarbageClassNbr(String garbageClassName, DocumentFile dataset){
        int nbrGarbage = 0;

        // doesn't need to check for the file existance because we already did that
        if(dataset!=null && dataset.canRead()){

            try {
                InputStream in = getContentResolver().openInputStream(dataset.getUri());
                if(in!=null){
                    BufferedReader buffer = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while((line = buffer.readLine())!=null){
                        if(line.split(",")[0].toString().equals(garbageClassName)){
                            nbrGarbage+=1;
                        }
                    }
                    buffer.close();
                    in.close();
                }
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        }
        return nbrGarbage;
    }

    @NonNull
    private Uri handleDirectory(@NonNull Intent data){
        Uri uri = data.getData();
        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return uri;
    }

    private void browseFolder(){
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        documentTreeLauncher.launch(i);
    }


    private void askForReadPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Request the permission if not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_SOTRAGE);

        } else {
            askForWritePermission();
        }
    }

    private void askForWritePermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Request the permission if not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_SOTRAGE);

        } else {
            browseFolder();
        }
    }
}