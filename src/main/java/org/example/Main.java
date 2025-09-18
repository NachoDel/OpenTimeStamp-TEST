package org.example;

import org.example.OtsService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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

            // Solicitar al usuario el número de opción
            Scanner scanner = new Scanner(System.in);
            System.out.println("Ingrese el número de la operación a realizar:");
            System.out.println("1: Timestamping");
            System.out.println("2: Info del stamp");
            System.out.println("3: Upgrade del .ots");
            System.out.println("4: Verificación del .ots");
            System.out.println("5: Upgrade + Verificación del .ots");
            System.out.println("6: Upgrade reiterado por 1 hora");
            int opcion = scanner.nextInt();

            // Switch case para elegir la operación
            switch (opcion) {
                case 1:
                    System.out.println("Intentando realizar el timestamping...");
                    OtsService.stampFile("/home/nacho/Documentos/PRUEBA OTS/Test2/test2.pdf");
                    System.out.println("Finalizado el proceso de timestamping");
                    break;
                case 2:
                    System.out.println("Mostrando información del stamp...");
                    OtsService.info(otsFilePath);
                    break;
                case 3:
                    System.out.println("Intentando realizar el upgrade...");
                    if (OtsService.upgradeOts(otsFilePath)){
                        System.out.println("Upgrade realizado con exito");
                    } else {
                        System.out.println("Upgrade FALLIDO");
                    }
                    break;
                case 4:
                    System.out.println("Intentando realizar la verificacion...");
                    if (OtsService.verifyOts(otsFilePath, filePath)){
                        System.out.println("Verificacion realizada con exito");
                    } else {
                        System.out.println("Verificacion FALLIDA");
                    }
                    break;
                case 5:
                    System.out.println("Intentando realizar el upgrade + verificacion...");
                    if (OtsService.upgradeAndVerify(otsFilePath, filePath)){
                        System.out.println("Upgrade + verificacion realizado con exito");
                    } else {
                        System.out.println("Upgrade + verificacion FALLIDO");
                    }
                    break;
                case 6:
                    System.out.println("Intentando realizar el upgrade reiterado por 1 hora...");
                    if (OtsService.upgradeWithRetries(otsFilePath, filePath)){
                        System.out.println("Upgrade reiterado por 1 hora realizado con exito");
                    } else {
                        System.out.println("Upgrade reiterado por 1 hora FALLIDO");
                    }
                    break;
                default:
                    System.out.println("Opción inválida.");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

