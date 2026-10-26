using System;
using System.IO;
using System.Management.Automation;
using System.Management.Automation.Runspaces;
using System.Threading;
using System.Windows.Forms;

internal static class HaiYanBackendHost {
    [STAThread]
    private static void Main(string[] args) {
        Application.EnableVisualStyles();
        Environment.SetEnvironmentVariable("PSExecutionPolicyPreference", "Bypass", EnvironmentVariableTarget.Process);
        try {
            using (var runspace = RunspaceFactory.CreateRunspace()) {
                runspace.ApartmentState = ApartmentState.STA;
                runspace.ThreadOptions = PSThreadOptions.UseCurrentThread;
                runspace.Open();
                using (var powershell = PowerShell.Create()) {
                    powershell.Runspace = runspace;
                    powershell.AddCommand(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "HaiYanBackend.ps1"));
                    if (Array.IndexOf(args, "--configure") >= 0) powershell.AddParameter("ConfigureOnly");
                    powershell.Invoke();
                    if (powershell.HadErrors) throw new Exception(powershell.Streams.Error[0].ToString());
                }
            }
        } catch (Exception error) {
            MessageBox.Show(error.Message, "海燕传信后端", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
