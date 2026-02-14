- The Repository pattern abstracts WHERE data comes from so the rest of
- the app doesn't need to know about Android's PackageManager, databases,
- or network APIs.
-
- This interface is the "D" in SOLID - Dependency Inversion Principle.
- High-level modules (like ViewModel) depend on this abstraction,
- not on low-level implementation details.

* What is a Repository?
* A Repository mediates between the domain and data mapping layers,
* acting like an in-memory collection of domain objects. It provides
* a clean API for accessing data while hiding implementation details.
*
* Why use a Repository interface?
* 1.  Testability: ViewModel can be tested with a mock implementation
* 2.  Flexibility: Can swap data sources without changing ViewModel
* 3.  Single Responsibility: All data access logic is in one place
* 4.  Abstraction: ViewModel doesn't know about PackageManager, DataStore, etc.
*
* Example usage in ViewModel:
* ```kotlin

  ```
* class MyViewModel(private val repository: AppRepository) {
*     suspend fun loadApps() {
*         val apps = repository.getInstalledApps() // Don't care WHERE from
*     }
* }
* ```

  ```
