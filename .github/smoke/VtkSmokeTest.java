import vtk.vtkNativeLibrary;
import vtk.vtkSphereSource;
import vtk.vtkPolyData;
import vtk.vtkTriangleFilter;

/**
 * CI smoke test: proves the VTK native libraries actually LOAD and RUN in a
 * JVM, not merely that they compiled and exported symbols.
 *
 * Loading strategy uses VTK's own loader via the `-Dvtk.lib.dir=<dir>` system
 * property (see vtkNativeLibrary.LoadLibrary): when that property is set, the
 * loader loads each lib by absolute path from that directory AND patches
 * java.library.path so inter-library dependencies resolve — so we don't have
 * to juggle LD_LIBRARY_PATH / DYLD_LIBRARY_PATH / PATH per platform.
 *
 * This tests LIBRARY SOUNDNESS in the friendly flat-directory CI case. It is
 * deliberately NOT a test of any downstream (e.g. NetBeans) loading logic.
 */
public class VtkSmokeTest {

    public static void main(String[] args) {
        // Fail loudly if not every library loaded. LoadAllNativeLibraries
        // returns false if ANY lib failed (and prints the stack trace itself).
        if (!vtkNativeLibrary.LoadAllNativeLibraries()) {
            System.out.println("FAIL: not all VTK native libraries loaded.");
            // List which ones failed, for a legible diagnostic.
            for (vtkNativeLibrary lib : vtkNativeLibrary.values()) {
                if (!lib.IsLoaded()) {
                    System.out.println("  NOT LOADED: " + lib.GetLibraryName());
                }
            }
            System.exit(1);
        }
        System.out.println("OK: all VTK native libraries loaded.");

        // Route VTK's error window to a log file rather than a popup/stderr.
        vtkNativeLibrary.DisableOutputWindow(null);

        // --- Exercise a real pipeline, not just a constructor ---
        // A constructor only proves the JNI symbol links. Running a filter
        // proves native COMPUTE works end to end and data crosses the JNI
        // boundary back into Java.
        vtkSphereSource sphere = new vtkSphereSource();
        sphere.SetThetaResolution(16);
        sphere.SetPhiResolution(16);
        sphere.Update();

        vtkPolyData sphereOut = sphere.GetOutput();
        long spherePoints = sphereOut.GetNumberOfPoints();
        System.out.println("Sphere produced " + spherePoints + " points.");

        // Chain a second filter to prove the pipeline connects across modules
        // (sources -> filters, i.e. two different native libs cooperating).
        vtkTriangleFilter tri = new vtkTriangleFilter();
        tri.SetInputConnection(sphere.GetOutputPort());
        tri.Update();
        long triCells = tri.GetOutput().GetNumberOfCells();
        System.out.println("TriangleFilter produced " + triCells + " cells.");

        if (spherePoints <= 0 || triCells <= 0) {
            System.out.println("FAIL: pipeline ran but produced empty output.");
            System.exit(1);
        }

        System.out.println("VTK smoke test PASSED.");
    }
}
