# Java Synchronization Examples

This repository contains two Java examples of thread synchronization using semaphores.

 Examples

 Readers–Writers

ReadersWriters.java starts 5 reader threads and 3 writer threads.
Multiple readers can read the shared value at the same time, while a writer gets exclusive access to update it. 
Each thread performs 3 operations.

 Dining Philosophers

`DiningPhilosophers.java` animates 4 philosophers sharing 4 chopsticks. Semaphores control access to the chopsticks, and a room semaphore allows at most 3 philosophers to try to eat at once. Each philosopher eats 3 times. The program opens a Swing window showing each philosopher's state and chopstick use.

