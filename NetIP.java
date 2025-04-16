import java.net.*;
import java.util.*;

/**
 * @author adrianroguez
 * @version 1.0
 */
public class NetIP {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println(); // Espacio vacio
        System.out.print("Introduce la IP con máscara (formato IP/MASCARA, ej: 192.168.1.0/24): ");
        String ipCidr = scanner.nextLine();
        boolean vlsm = false;
        boolean debug = false;
        System.out.print("¿Deseas activar el modo VLSM? (s/n): ");
        String vlsmInput = scanner.nextLine();
        if (vlsmInput.equalsIgnoreCase("s")) {
            vlsm = true;
        }
        System.out.print("¿Deseas activar el modo debug? (s/n): ");
        String debugInput = scanner.nextLine();
        if (debugInput.equalsIgnoreCase("s")) {
            debug = true;
        }
        try {
            if (!ipCidr.matches("\\d{1,3}(\\.\\d{1,3}){3}/\\d{1,2}")) {
                throw new IllegalArgumentException("Formato incorrecto. Usa IP/MASCARA (ej. 192.168.1.0/24)");
            }

            String[] parts = ipCidr.split("/");
            String ipStr = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            if (prefix < 0 || prefix > 32)
                throw new IllegalArgumentException("Máscara inválida.");

            InetAddress ip = InetAddress.getByName(ipStr);
            byte[] ipBytes = ip.getAddress();
            int ipInt = byteArrayToInt(ipBytes);

            int mask = prefixToMask(prefix);
            int network = ipInt & mask;
            int broadcast = network | ~mask;

            if (!vlsm) {
                printBasicInfo(ipInt, prefix, network, broadcast, debug);
            } else {
                System.out.print("¿Cuántas subredes necesitas?: ");
                int numSubnets = scanner.nextInt();

                List<Integer> hostCounts = new ArrayList<>();
                for (int i = 0; i < numSubnets; i++) {
                    System.out.print("Subred " + (i + 1) + " - Nº de Hosts requeridos: ");
                    hostCounts.add(scanner.nextInt());
                }
                performVLSM(network, prefix, hostCounts, debug);
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Metodo que Muestra información basica de la red: clase, tipo, red, broadcast
     * y rango
     * 
     * @param ip        IP en formato numero entero
     * @param prefix    Prefijo en formato entero
     * @param network   Direccion de red en formato entero
     * @param broadcast Broadcast en formato entero
     * @param debug     Si es true, activa la salida en modo detallado (binario)
     * @throws Exception
     */
    public static void printBasicInfo(int ip, int prefix, int network, int broadcast, boolean debug) throws Exception {
        System.out.println("Clase: " + ipClass(ip));
        System.out.println("Tipo: " + ipType(ip));
        System.out.println("Dirección de red: " + intToIp(network));
        System.out.println("Broadcast: " + intToIp(broadcast));

        if (prefix < 31) {
            System.out.println("Rango disponible: " + intToIp(network + 1) + " - " + intToIp(broadcast - 1));
        } else {
            System.out.println("Rango disponible: No hay hosts disponibles");
        }

        if (debug) {
            System.out.println(
                    "[DEBUG] IP binaria: " + String.format("%32s", Integer.toBinaryString(ip)).replace(' ', '0'));
            System.out.println("[DEBUG] Máscara binaria: "
                    + String.format("%32s", Integer.toBinaryString(prefixToMask(prefix))).replace(' ', '0'));
        }
    }

    /**
     * Metodo que realiza el cálculo de subredes utilizando VLSM segun los
     * requerimientos de hosts
     * 
     * @param baseNetwork Direccion de red base como entero
     * @param basePrefix  Prefijo de la red base
     * @param hosts       Lista con el numero de hosts requeridos por subred
     * @param debug       Si es true, imprime detalles binarios de cada calculo
     */
    public static void performVLSM(int baseNetwork, int basePrefix, List<Integer> hosts, boolean debug) {
        hosts.sort((a, b) -> b - a);

        int nextNetwork = baseNetwork;
        System.out.printf("\n%-7s %-10s %-15s %-8s %-15s %-15s %-15s\n",
                "Subred", "Nº Hosts", "IP Red", "Máscara", "Primer Host", "Último Host", "Broadcast");

        for (int i = 0; i < hosts.size(); i++) {
            int needed = hosts.get(i) + 2; // +2 por red y broadcast
            int bits = (int) Math.ceil(Math.log(needed) / Math.log(2));
            int newPrefix = 32 - bits;
            int subnetMask = prefixToMask(newPrefix);
            int subnetBroadcast = nextNetwork | ~subnetMask;

            if ((subnetBroadcast & 0xFFFFFFFFL) > ((baseNetwork | ~prefixToMask(basePrefix)) & 0xFFFFFFFFL)) {
                System.out.println("\n[ERROR] No hay espacio suficiente para la subred " + (i + 1));
                return;
            }

            int firstHost = nextNetwork + 1;
            int lastHost = subnetBroadcast - 1;

            System.out.printf("%-7d %-10d %-15s /%-7d %-15s %-15s %-15s\n",
                    (i + 1), hosts.get(i), intToIp(nextNetwork), newPrefix,
                    intToIp(firstHost), intToIp(lastHost), intToIp(subnetBroadcast));

            if (debug) {
                System.out.println("[DEBUG] Subred " + (i + 1) + " binario: "
                        + String.format("%32s", Integer.toBinaryString(nextNetwork)).replace(' ', '0'));
                System.out.println("[DEBUG] Máscara /" + newPrefix + ": "
                        + String.format("%32s", Integer.toBinaryString(subnetMask)).replace(' ', '0'));
            }

            nextNetwork = subnetBroadcast + 1;
        }
    }

    /**
     * Metodo que convierte un numero entero a una direccion IP en formato string
     * 
     * @param ip IP en formato numero entero
     * @return IP en formato string
     */
    public static String intToIp(int ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    /**
     * Metodo que convierte una direccion IP en formato byte[] a entero
     * 
     * @param bytes IP en formato byte[]
     * @return IP en formato entero
     */
    public static int byteArrayToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    /**
     * Metodo que convierte un prefijo (como /24) en una mascara en formato entero
     * 
     * @param prefix Prefijo en formato entero
     * @return Mascara en formato entero
     */
    public static int prefixToMask(int prefix) {
        return (int) (0xFFFFFFFFL << (32 - prefix));
    }

    /**
     * Metodo que determina la clase de una direccion IP (A, B, C, D, E)
     * 
     * @param ip IP en formato entero
     * @return La clase de la direccion IP en String
     */
    public static String ipClass(int ip) {
        int first = (ip >> 24) & 0xFF;
        if (first <= 126)
            return "A";
        else if (first <= 191)
            return "B";
        else if (first <= 223)
            return "C";
        else if (first <= 239)
            return "D";
        else
            return "E";
    }

    /**
     * Metodo que determina si una IP es publica o privada
     * 
     * @param ip IP en formato entero
     * @return String publica o String privada
     */
    public static String ipType(int ip) {
        int first = (ip >> 24) & 0xFF;
        int second = (ip >> 16) & 0xFF;

        if (first == 10)
            return "Privada";
        if (first == 172 && (second >= 16 && second <= 31))
            return "Privada";
        if (first == 192 && second == 168)
            return "Privada";
        return "Pública";
    }
}
