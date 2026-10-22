import SwiftUI

struct ContentView: View {
    @State private var isBreakTimerRunning = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    distanceCard
                    breakCard
                    progressCard
                }
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("EyeGuard")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Build healthier screen habits")
                .font(.title2.weight(.bold))

            Text("Small reminders. Comfortable distance. Better breaks.")
                .foregroundStyle(.secondary)
        }
    }

    private var distanceCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Distance Guardian", systemImage: "ruler")
                .font(.headline)

            HStack {
                Image(systemName: "face.smiling")
                    .font(.system(size: 38))
                    .foregroundStyle(.green)

                VStack(alignment: .leading) {
                    Text("Monitoring not started")
                        .font(.title3.weight(.semibold))
                    Text("TrueDepth will be added in the next stage.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var breakCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("20-Minute Reset", systemImage: "leaf.fill")
                .font(.headline)

            Text(isBreakTimerRunning ? "Your reset is ready to begin." : "Take a short visual break when you need one.")
                .foregroundStyle(.secondary)

            Button(isBreakTimerRunning ? "Break Started" : "Start a Test Break") {
                isBreakTimerRunning = true
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var progressCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Today", systemImage: "chart.bar.fill")
                .font(.headline)

            HStack {
                progressItem(title: "Breaks", value: "0")
                Spacer()
                progressItem(title: "XP", value: "0")
                Spacer()
                progressItem(title: "Streak", value: "0 days")
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func progressItem(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.title3.weight(.bold))
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    ContentView()
}
