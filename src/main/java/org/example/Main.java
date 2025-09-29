package org.example;

import org.example.OtsService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            // ------------- File a timestampear -------------
            String filePathString = "/home/nacho/Documentos/PRUEBA OTS/PDF para test-PPS/test_signed_token.pdf";
            Path filePath = Paths.get(filePathString);
            //Path filePath = null;

            //Path del archivo .ots a upgradear/verificar
            String otsFilePathString = "/home/nacho/Documentos/PRUEBA OTS/Test2/OTS con upgrade/test2.pdf.ots";
            Path otsFilePath = Paths.get(otsFilePathString);
            //Path otsFilePath = null;
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
            System.out.print("< ");
            int opcion = scanner.nextInt();

            //Condiciones que requieren el path del archivo
//            if (opcion == 1 || opcion == 4 || opcion == 5 || opcion == 6) {
//                do {
//                    filePath = entradaFile();
//                    if (filePath == null || !filePath.toFile().exists()) {
//                        System.out.println("El archivo a stamp no existe. Por favor, ingrese una ruta válida.");
//                    }
//                } while(filePath ==null || !filePath.toFile().exists());
//            }
//            //Condiciones que requieren el path del archivo .ots
//            if (opcion == 2 || opcion == 3 || opcion == 4 || opcion == 5 || opcion == 6){
//                do {
//                    otsFilePath = entradaOts();
//                    if (otsFilePath == null || !otsFilePath.toFile().exists()) {
//                        System.out.println("El archivo .ots no existe. Por favor, ingrese una ruta válida.");
//                    }
//                } while(otsFilePath == null || !otsFilePath.toFile().exists());
//            }
            // Switch case para elegir la operación
            switch (opcion) {
                case 1:
                    System.out.println("Intentando realizar el timestamping...");
                    OtsService.stampFile(filePath);
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

    /*
    * Metodo utilizado para solicitar la entrada del path del archivo a stampear/verificar
    */
    private static Path entradaFile(){
        Scanner scanner = new Scanner(System.in);
        System.out.print("Ingrese la ruta del archivo original: ");
        String filePathStr = scanner.nextLine().trim();

        return Paths.get(filePathStr).toAbsolutePath();
    }

    /*
     * Metodo utilizado para solicitar la entrada del path del archivo ots a upgradear/verificar
     */
    private static Path entradaOts(){
        Scanner scanner = new Scanner(System.in);
        System.out.print("Ingrese la ruta del archivo .ots: ");
        String filePathStr = scanner.nextLine().trim();

        return Paths.get(filePathStr).toAbsolutePath();
    }

}

