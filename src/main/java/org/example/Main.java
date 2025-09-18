package org.example;

import org.example.OtsService;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {

            //Variables para usar como path
            // ------------- File a timestampear -------------
            String filePathString = "//home/nacho/Documentos/PRUEBA OTS/Test2/test2.pdf";
            Path filePath = Paths.get(filePathString);

            //Path del archivo .ots a upgradear/verificar
            String otsFilePathString = "/home/nacho/Documentos/PRUEBA OTS/Test2/OTS con upgrade/test2.pdf.ots";
            Path otsFilePath = Paths.get(otsFilePathString);
            // ------------- File a timestampear -------------

            // ---------- Metodo de timestampeado ----------
            /*
            System.out.println("Intentando realizar el timestamping...");
            // Acá pasás la ruta del archivo que quieras timestampear como String
            OtsService.stampFile("/home/nacho/Documentos/PRUEBA OTS/Test2/test2.pdf");

            //OtsService.stampFile(filePath); //CASO QUE SE QUIERA PASAR COMO PATH DIRECTAMENTE

            System.out.println("Finalizado el proceso de timestamping");
             */
            // ---------- Metodo de timestampeado ---------- */

            // --------- Metodo de upgrade del .ots ----------
            /*
            System.out.println("Intentando realizar el upgrade...");
            if (OtsService.upgradeOts(otsFilePath)){
                System.out.println("Upgrade realizado con exito");
            }
            else{
                System.out.println("Upgrade FALLIDO");
            }
            */
            // ---------- Metodo de upgrade del .ots ----------

            // --------- Metodo de verificacion del .ots ----------

            System.out.println("Intentando realizar la verificacion...");
            if (OtsService.verifyOts(otsFilePath, filePath)){
                System.out.println("Verificacion realizada con exito");
            }
            else {
                System.out.println("Verificacion FALLIDA");
            }

            // --------- Metodo de verificacion del .ots ----------

            // --------- Metodo de upgrade + verificacion del .ots ----------
            /*
            System.out.println("Intentando realizar el upgrade + verificacion...");
            if (OtsService.upgradeAndVerify(otsFilePath, filePath)){
                System.out.println("Upgrade + verificacion realizado con exito");
            }
            else {
                System.out.println("Upgrade + verificacion FALLIDO");
            }
            */
            // --------- Metodo de upgrade + verificacion del .ots ----------

            // --------- Upgrade + Verify reiterado por 1 hora ----------
            /*
            System.out.println("Intentando realizar el upgrade reiterado por 1 hora...");
            if (OtsService.upgradeWithRetries(otsFilePath, filePath)){
                System.out.println("Upgrade reiterado por 1 hora realizado con exito");
            }
            else {
                System.out.println("Upgrade reiterado por 1 hora FALLIDO");
            }
            */
            // --------- Upgrade + verify reiterado por 1 hora ----------

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

